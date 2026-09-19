package com.meridiantrust.sentinel.detection.rule.impl;


import com.meridiantrust.sentinel.common.model.Formatting;
import com.meridiantrust.sentinel.detection.model.*;
import com.meridiantrust.sentinel.detection.rule.*;
import com.meridiantrust.sentinel.detection.service.*;
import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repeated suspiciously round amounts (problem statement section B).
 *
 * <p>Organic commerce produces untidy numbers: 4,732.18 for a purchase,
 * 61,450.00 for a salary. Repeated exact multiples of 50,000 are a sign of
 * amounts being <em>constructed</em> rather than earned or spent — someone
 * deciding how much to move, rather than paying for something.
 *
 * <p>Weighted lowest of the six rules, deliberately. On its own a round amount
 * is weak evidence and would not justify an analyst's attention. Its value is
 * corroborative: combined with structuring or layering on the same customer, it
 * lifts the case's aggregate score (see the noisy-OR aggregation in
 * {@code RiskScoringService}). This is why scoring is separated from detection
 * — a weak signal can contribute without generating noise on its own.
 */
@Component
public class RoundAmountPatternRule extends AbstractDetectionRule {

    public static final String CODE = "ROUND_AMOUNT_PATTERN";

    private static final int DEFAULT_MIN_COUNT = 3;
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final BigDecimal DEFAULT_UNIT = BigDecimal.valueOf(10_000);
    private static final BigDecimal DEFAULT_MIN_AMOUNT = BigDecimal.valueOf(50_000);

    public RoundAmountPatternRule(RuleConfigProvider configProvider) {
        super(configProvider);
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public String typology() {
        return "ROUND_AMOUNT";
    }

    @Override
    public RuleScope scope() {
        return RuleScope.ACCOUNT_WINDOW;
    }

    @Override
    public List<RuleHit> evaluate(RuleContext context) {
        int minCount = params().getInt("minCount", DEFAULT_MIN_COUNT);
        int windowHours = params().getInt("windowHours", DEFAULT_WINDOW_HOURS);
        BigDecimal unit = params().getDecimal("roundingUnit", DEFAULT_UNIT);
        BigDecimal minAmount = params().getDecimal("minAmountBase", DEFAULT_MIN_AMOUNT);

        List<RuleHit> hits = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : context.accountWindows().entrySet()) {
            String accountId = entry.getKey();

            List<Transaction> candidates = entry.getValue().stream()
                    .filter(t -> t.baseAmount().isGreaterThanOrEqual(minAmount))
                    .filter(t -> t.baseAmount().isExactMultipleOf(unit))
                    .toList();

            for (List<Transaction> cluster : SlidingWindow.findClusters(candidates, windowHours, minCount)) {
                BigDecimal total = cluster.stream()
                        .map(Transaction::getAmountBase)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String dedupKey = dedupKeyForAccountDay(accountId, cluster.get(0).getTxnTimestamp());
                String explanation = ("%d transactions on account %s within %d hours were exact multiples "
                        + "of %s, totalling %s. Repeated round-number amounts suggest values chosen rather "
                        + "than arising from genuine commercial activity.")
                        .formatted(cluster.size(),
                                accountId,
                                windowHours,
                                Formatting.money(unit),
                                Formatting.money(total));

                hits.add(hit()
                        .customerId(cluster.get(0).getCustomerId())
                        .accountId(accountId)
                        .dedupKey(dedupKey)
                        .explanation(explanation)
                        .evidenceTransactionIds(cluster.stream().map(Transaction::getTransactionId).toList())
                        .evidenceAmountBase(total)
                        .magnitudeRatio(BigDecimal.valueOf(cluster.size())
                                .divide(BigDecimal.valueOf(minCount), 4, RoundingMode.HALF_UP))
                        .build());
                traceHit(dedupKey, explanation);
            }
        }
        return hits;
    }
}
