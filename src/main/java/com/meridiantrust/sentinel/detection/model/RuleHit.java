package com.meridiantrust.sentinel.detection.model;


import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * The immutable output of a rule evaluation.
 *
 * <p>Note what this is <em>not</em>: it is not an {@code Alert}. Rules report
 * findings; scoring and alert construction happen downstream in the alerting
 * module. Keeping the two apart means a rule cannot accidentally couple itself
 * to persistence or to the scoring model, and a rule can be unit-tested by
 * asserting on a plain value object.
 *
 * @param jurisdictionUplift risk-score contribution from a sanctions/high-risk
 *                           match, 0 when not applicable
 * @param magnitudeRatio     how far the observed value exceeded the rule's
 *                           threshold; feeds the magnitude uplift in scoring
 */
@Builder
public record RuleHit(
        String ruleCode,
        String typology,
        String customerId,
        String accountId,
        String dedupKey,
        String explanation,
        List<String> evidenceTransactionIds,
        BigDecimal evidenceAmountBase,
        int jurisdictionUplift,
        BigDecimal magnitudeRatio) {

    public RuleHit {
        evidenceTransactionIds = evidenceTransactionIds == null
                ? List.of()
                : List.copyOf(evidenceTransactionIds);
        magnitudeRatio = magnitudeRatio == null ? BigDecimal.ONE : magnitudeRatio;
        evidenceAmountBase = evidenceAmountBase == null ? BigDecimal.ZERO : evidenceAmountBase;
    }
}
