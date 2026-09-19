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

/**
 * <b>Business rule 1</b> — any single transaction at or above the reporting
 * threshold is automatically flagged for review (CTR-style).
 *
 * <p>Operates on the base-currency amount, so a USD transfer that only breaches
 * the threshold after conversion is caught too. Checking the raw amount instead
 * would let a 10,000 USD transfer pass a 10,000 INR threshold unexamined —
 * which is precisely the gap business rule 9 exists to close.
 */
@Component
public class CtrThresholdRule extends AbstractDetectionRule {

    public static final String CODE = "CTR_THRESHOLD";
    private static final BigDecimal DEFAULT_THRESHOLD = BigDecimal.valueOf(10_000);

    public CtrThresholdRule(RuleConfigProvider configProvider) {
        super(configProvider);
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public String typology() {
        return "THRESHOLD_BREACH";
    }

    @Override
    public RuleScope scope() {
        return RuleScope.TRANSACTION;
    }

    @Override
    public List<RuleHit> evaluate(RuleContext context) {
        BigDecimal threshold = params().getDecimal("thresholdBase", DEFAULT_THRESHOLD);
        List<RuleHit> hits = new ArrayList<>();

        for (Transaction txn : context.transactions()) {
            if (!txn.baseAmount().isGreaterThanOrEqual(threshold)) {
                continue;
            }
            String dedupKey = dedupKeyForTransaction(txn.getTransactionId());
            String explanation = ("Transaction %s (%s, %s) on account %s met or exceeded the %s "
                    + "reporting threshold and is automatically flagged for review.")
                    .formatted(txn.getTransactionId(),
                            Formatting.money(txn.getAmountBase()),
                            Formatting.timestamp(txn.getTxnTimestamp()),
                            txn.getAccountId(),
                            Formatting.money(threshold));

            hits.add(hit()
                    .customerId(txn.getCustomerId())
                    .accountId(txn.getAccountId())
                    .dedupKey(dedupKey)
                    .explanation(explanation)
                    .evidenceTransactionIds(List.of(txn.getTransactionId()))
                    .evidenceAmountBase(txn.getAmountBase())
                    .magnitudeRatio(ratio(txn.getAmountBase(), threshold))
                    .build());
            traceHit(dedupKey, explanation);
        }
        return hits;
    }

    private BigDecimal ratio(BigDecimal amount, BigDecimal threshold) {
        if (threshold.signum() == 0) {
            return BigDecimal.ONE;
        }
        return amount.divide(threshold, 4, RoundingMode.HALF_UP);
    }
}
