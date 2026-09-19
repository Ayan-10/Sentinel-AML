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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>Business rule 3</b> — rapid movement of funds (layering). Funds are
 * deposited and at least 80% of that value leaves the account within 48 hours.
 *
 * <p>Layering is the middle stage of laundering: money is moved quickly through
 * accounts to break the audit trail between the predicate crime and the final
 * placement. A legitimate account accumulates; a conduit account passes value
 * straight through, which is what this measures.
 *
 * <p>The {@code minDepositBase} floor matters. Without it, an ordinary salary
 * credit spent over the following two days is an 80%+ outflow and would alert —
 * which describes most of the bank's customers. The floor is what separates a
 * conduit from normal life.
 */
@Component
public class RapidMovementRule extends AbstractDetectionRule {

    public static final String CODE = "RAPID_MOVEMENT";

    private static final int DEFAULT_WINDOW_HOURS = 48;
    private static final BigDecimal DEFAULT_RATIO = new BigDecimal("0.80");
    private static final BigDecimal DEFAULT_MIN_DEPOSIT = BigDecimal.valueOf(50_000);

    public RapidMovementRule(RuleConfigProvider configProvider) {
        super(configProvider);
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public String typology() {
        return "LAYERING";
    }

    @Override
    public RuleScope scope() {
        return RuleScope.ACCOUNT_WINDOW;
    }

    @Override
    public List<RuleHit> evaluate(RuleContext context) {
        int windowHours = params().getInt("windowHours", DEFAULT_WINDOW_HOURS);
        BigDecimal requiredRatio = params().getDecimal("outflowRatio", DEFAULT_RATIO);
        BigDecimal minDeposit = params().getDecimal("minDepositBase", DEFAULT_MIN_DEPOSIT);
        Duration window = Duration.ofHours(windowHours);

        List<RuleHit> hits = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : context.accountWindows().entrySet()) {
            String accountId = entry.getKey();
            List<Transaction> windowTransactions = entry.getValue();

            for (Transaction deposit : windowTransactions) {
                if (!deposit.isCredit() || !deposit.baseAmount().isGreaterThanOrEqual(minDeposit)) {
                    continue;
                }

                LocalDateTime from = deposit.getTxnTimestamp();
                LocalDateTime to = from.plus(window);

                List<Transaction> outflows = windowTransactions.stream()
                        .filter(Transaction::isDebit)
                        .filter(t -> !t.getTxnTimestamp().isBefore(from) && !t.getTxnTimestamp().isAfter(to))
                        .toList();

                if (outflows.isEmpty()) {
                    continue;
                }

                BigDecimal outflowTotal = outflows.stream()
                        .map(Transaction::getAmountBase)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal ratio = outflowTotal.divide(deposit.getAmountBase(), 6, RoundingMode.HALF_UP);

                if (ratio.compareTo(requiredRatio) < 0) {
                    continue;
                }

                // Anchored to the deposit: repeated detection of the same
                // deposit-and-dispersal folds into one alert, not one per outflow.
                String dedupKey = dedupKeyForAnchor(accountId, deposit.getTransactionId());
                String explanation = ("%s of a %s deposit (transaction %s received %s) left account %s "
                        + "within %d hours, across %d outbound transaction(s) totalling %s. "
                        + "Funds passing straight through an account is consistent with layering.")
                        .formatted(Formatting.percent(ratio),
                                Formatting.money(deposit.getAmountBase()),
                                deposit.getTransactionId(),
                                Formatting.timestamp(from),
                                accountId,
                                windowHours,
                                outflows.size(),
                                Formatting.money(outflowTotal));

                List<String> evidence = new ArrayList<>();
                evidence.add(deposit.getTransactionId());
                outflows.forEach(t -> evidence.add(t.getTransactionId()));

                hits.add(hit()
                        .customerId(deposit.getCustomerId())
                        .accountId(accountId)
                        .dedupKey(dedupKey)
                        .explanation(explanation)
                        .evidenceTransactionIds(evidence)
                        .evidenceAmountBase(outflowTotal)
                        .magnitudeRatio(ratio.divide(requiredRatio, 4, RoundingMode.HALF_UP))
                        .build());
                traceHit(dedupKey, explanation);
            }
        }
        return hits;
    }
}
