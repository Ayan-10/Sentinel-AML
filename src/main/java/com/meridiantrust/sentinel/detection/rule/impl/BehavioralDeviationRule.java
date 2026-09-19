package com.meridiantrust.sentinel.detection.rule.impl;

import com.meridiantrust.sentinel.customer.model.Customer;

import com.meridiantrust.sentinel.common.model.Formatting;
import com.meridiantrust.sentinel.detection.model.*;
import com.meridiantrust.sentinel.detection.rule.*;
import com.meridiantrust.sentinel.detection.service.*;
import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import com.meridiantrust.sentinel.transaction.model.DailyVolume;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <b>Business rule 5</b> — a customer's daily transaction value exceeding three
 * times their 90-day rolling average triggers a behavioural deviation alert.
 *
 * <p>Unlike the other rules, this one has no absolute threshold: it compares a
 * customer against their own history. That makes it the most valuable rule for
 * catching accounts whose behaviour changes — and the most dangerous for false
 * positives, because the ratio misbehaves badly at the edges.
 *
 * <p>Two guards address that, and both are load-bearing:
 * <ul>
 *   <li><b>{@code minBaselineTxns}</b> — a customer with two prior transactions
 *       has no meaningful average. Without this guard every new customer's
 *       third transaction is a "3x deviation" and the queue fills with
 *       onboarding noise.</li>
 *   <li><b>{@code minDailyBase}</b> — a customer whose baseline is 200 rupees
 *       triples it by buying lunch. An alert must represent money worth an
 *       analyst's time.</li>
 * </ul>
 * Without these, this rule alone would generate more alerts than the other five
 * combined, and every one of them would be noise.
 */
@Component
public class BehavioralDeviationRule extends AbstractDetectionRule {

    public static final String CODE = "BEHAVIORAL_DEVIATION";

    private static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("3.0");
    private static final int DEFAULT_BASELINE_DAYS = 90;
    private static final int DEFAULT_MIN_BASELINE_TXNS = 5;
    private static final BigDecimal DEFAULT_MIN_DAILY = BigDecimal.valueOf(25_000);

    public BehavioralDeviationRule(RuleConfigProvider configProvider) {
        super(configProvider);
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public String typology() {
        return "BEHAVIOURAL_ANOMALY";
    }

    @Override
    public RuleScope scope() {
        return RuleScope.CUSTOMER_WINDOW;
    }

    @Override
    public List<RuleHit> evaluate(RuleContext context) {
        BigDecimal multiplier = params().getDecimal("multiplier", DEFAULT_MULTIPLIER);
        int baselineDays = params().getInt("baselineDays", DEFAULT_BASELINE_DAYS);
        int minBaselineTxns = params().getInt("minBaselineTxns", DEFAULT_MIN_BASELINE_TXNS);
        BigDecimal minDaily = params().getDecimal("minDailyBase", DEFAULT_MIN_DAILY);

        List<RuleHit> hits = new ArrayList<>();

        // Only evaluate days that this chunk actually touched — re-scoring a
        // customer's whole history on every ingestion would be wasteful and
        // would re-raise settled alerts.
        Map<String, Set<LocalDate>> daysInChunk = context.transactions().stream()
                .collect(Collectors.groupingBy(Transaction::getCustomerId,
                        Collectors.mapping(t -> t.getTxnTimestamp().toLocalDate(), Collectors.toSet())));

        for (Map.Entry<String, Set<LocalDate>> entry : daysInChunk.entrySet()) {
            String customerId = entry.getKey();
            List<DailyVolume> history = context.baselineFor(customerId);
            if (history.isEmpty()) {
                continue;
            }

            Map<LocalDate, DailyVolume> byDay = history.stream()
                    .collect(Collectors.toMap(DailyVolume::day, v -> v, (a, b) -> a));

            for (LocalDate day : entry.getValue()) {
                DailyVolume today = byDay.get(day);
                if (today == null || today.totalBase().compareTo(minDaily) < 0) {
                    continue;
                }

                // Baseline: the preceding N days, excluding the day under test —
                // including it would dilute the very spike we are measuring.
                LocalDate baselineStart = day.minusDays(baselineDays);
                List<DailyVolume> baseline = history.stream()
                        .filter(v -> v.day().isBefore(day) && !v.day().isBefore(baselineStart))
                        .toList();

                long baselineTxnCount = baseline.stream().mapToLong(DailyVolume::txnCount).sum();
                if (baselineTxnCount < minBaselineTxns) {
                    continue;   // cold start — no defensible average yet
                }

                BigDecimal baselineTotal = baseline.stream()
                        .map(DailyVolume::totalBase)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Averaged over elapsed days, not active days: a customer who
                // transacts twice a month has a low daily average, and that is
                // the correct baseline for "unusual for them".
                long elapsedDays = Math.max(1, baselineDays);
                BigDecimal average = baselineTotal.divide(
                        BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);

                if (average.signum() == 0) {
                    continue;
                }

                BigDecimal observedMultiple = today.totalBase().divide(average, 4, RoundingMode.HALF_UP);
                if (observedMultiple.compareTo(multiplier) <= 0) {
                    continue;
                }

                List<String> evidence = context.transactions().stream()
                        .filter(t -> t.getCustomerId().equals(customerId))
                        .filter(t -> t.getTxnTimestamp().toLocalDate().equals(day))
                        .map(Transaction::getTransactionId)
                        .toList();

                String dedupKey = dedupKeyForCustomerDay(customerId, day);
                String explanation = ("Customer %s transacted %s across %d transaction(s) on %s — %s their "
                        + "%d-day rolling average of %s per day. A sudden departure from an established "
                        + "pattern is a behavioural indicator of account misuse.")
                        .formatted(customerId,
                                Formatting.money(today.totalBase()),
                                today.txnCount(),
                                day,
                                Formatting.multiplier(observedMultiple),
                                baselineDays,
                                Formatting.money(average));

                hits.add(hit()
                        .customerId(customerId)
                        .accountId(evidence.isEmpty() ? null : accountOf(context, evidence.get(0)))
                        .dedupKey(dedupKey)
                        .explanation(explanation)
                        .evidenceTransactionIds(evidence)
                        .evidenceAmountBase(today.totalBase())
                        .magnitudeRatio(observedMultiple.divide(multiplier, 4, RoundingMode.HALF_UP))
                        .build());
                traceHit(dedupKey, explanation);
            }
        }
        return hits;
    }

    private String accountOf(RuleContext context, String transactionId) {
        return context.transactions().stream()
                .filter(t -> t.getTransactionId().equals(transactionId))
                .map(Transaction::getAccountId)
                .findFirst()
                .orElse(null);
    }
}
