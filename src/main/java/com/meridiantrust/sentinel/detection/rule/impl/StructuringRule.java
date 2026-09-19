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
 * <b>Business rule 2</b> — structuring / smurfing. Three or more transactions
 * from the same account inside a 24-hour window that each fall just below the
 * reporting threshold.
 *
 * <p>This is the classic evasion pattern: a launderer who knows about the
 * 10,000 reporting threshold splits 30,000 into four deposits of 9,400. Each is
 * individually unremarkable; together they are the signature.
 *
 * <p>The band is <em>inclusive at both ends</em> — 9,000.00 and 9,999.99 both
 * qualify. That boundary is a compliance decision, not an implementation
 * detail, so it is explicitly configured and explicitly tested.
 */
@Component
public class StructuringRule extends AbstractDetectionRule {

    public static final String CODE = "STRUCTURING";

    private static final int DEFAULT_MIN_COUNT = 3;
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final BigDecimal DEFAULT_LOWER = BigDecimal.valueOf(9_000);
    private static final BigDecimal DEFAULT_UPPER = new BigDecimal("9999.99");

    public StructuringRule(RuleConfigProvider configProvider) {
        super(configProvider);
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public String typology() {
        return "STRUCTURING";
    }

    @Override
    public RuleScope scope() {
        return RuleScope.ACCOUNT_WINDOW;
    }

    @Override
    public List<RuleHit> evaluate(RuleContext context) {
        int minCount = params().getInt("minCount", DEFAULT_MIN_COUNT);
        int windowHours = params().getInt("windowHours", DEFAULT_WINDOW_HOURS);
        BigDecimal lower = params().getDecimal("lowerBound", DEFAULT_LOWER);
        BigDecimal upper = params().getDecimal("upperBound", DEFAULT_UPPER);

        List<RuleHit> hits = new ArrayList<>();

        for (Map.Entry<String, List<Transaction>> entry : context.accountWindows().entrySet()) {
            String accountId = entry.getKey();

            // Only amounts inside the just-below-threshold band qualify.
            List<Transaction> candidates = entry.getValue().stream()
                    .filter(t -> t.baseAmount().isBetweenInclusive(lower, upper))
                    .toList();

            for (List<Transaction> cluster : SlidingWindow.findClusters(candidates, windowHours, minCount)) {
                BigDecimal total = cluster.stream()
                        .map(Transaction::getAmountBase)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                String dedupKey = dedupKeyForAccountDay(accountId, cluster.get(0).getTxnTimestamp());
                String explanation = ("%d transactions totalling %s were made on account %s within %d hours "
                        + "(%s to %s). Each fell between %s and %s — individually below the reporting "
                        + "threshold, consistent with deliberate structuring to avoid it.")
                        .formatted(cluster.size(),
                                Formatting.money(total),
                                accountId,
                                windowHours,
                                Formatting.timestamp(cluster.get(0).getTxnTimestamp()),
                                Formatting.timestamp(cluster.get(cluster.size() - 1).getTxnTimestamp()),
                                Formatting.money(lower),
                                Formatting.money(upper));

                hits.add(hit()
                        .customerId(cluster.get(0).getCustomerId())
                        .accountId(accountId)
                        .dedupKey(dedupKey)
                        .explanation(explanation)
                        .evidenceTransactionIds(cluster.stream().map(Transaction::getTransactionId).toList())
                        .evidenceAmountBase(total)
                        // More transactions than the minimum is a stronger signal.
                        .magnitudeRatio(BigDecimal.valueOf(cluster.size())
                                .divide(BigDecimal.valueOf(minCount), 4, RoundingMode.HALF_UP))
                        .build());
                traceHit(dedupKey, explanation);
            }
        }
        return hits;
    }
}
