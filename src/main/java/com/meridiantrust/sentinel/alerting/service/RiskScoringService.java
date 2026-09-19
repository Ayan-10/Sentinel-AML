package com.meridiantrust.sentinel.alerting.service;

import com.meridiantrust.sentinel.alerting.model.ScoreBreakdown;

import com.meridiantrust.sentinel.common.model.RiskRating;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Business rule 7: derives a 0-100 risk score from a weighted combination of
 * the triggered rule and its context.
 *
 * <p>Scoring is deliberately <em>outside</em> the rules. A rule answers "did
 * this pattern occur?"; scoring answers "how much should an analyst care?".
 * Separating them means the scoring model can be retuned — or replaced with the
 * ML model in the extension ideas — without touching detection logic, and a
 * rule cannot quietly inflate its own importance.
 */
@Service
public class RiskScoringService {

    private static final int MAX_SCORE = 100;
    private static final int MAX_MAGNITUDE_UPLIFT = 15;
    private static final int MAX_RECURRENCE_UPLIFT = 12;
    private static final int RECURRENCE_STEP = 3;
    private static final int PEP_UPLIFT = 10;

    /**
     * Scores a single alert.
     *
     * @param priorTriggerCount how many times this pattern has already been
     *                          detected; repetition is itself evidence
     */
    public ScoreBreakdown score(RuleHit hit, int ruleWeight, Customer customer, int priorTriggerCount) {
        int jurisdiction = Math.max(0, hit.jurisdictionUplift());
        int customerRisk = customer == null
                ? 0
                : (customer.getRiskRating() == null ? RiskRating.LOW : customer.getRiskRating()).scoreUplift();
        int pep = (customer != null && customer.isPoliticallyExposed()) ? PEP_UPLIFT : 0;
        int magnitude = magnitudeUplift(hit.magnitudeRatio());
        int recurrence = Math.min(MAX_RECURRENCE_UPLIFT, Math.max(0, priorTriggerCount) * RECURRENCE_STEP);

        int total = clamp(ruleWeight + jurisdiction + customerRisk + pep + magnitude + recurrence);

        return new ScoreBreakdown(ruleWeight, jurisdiction, customerRisk, pep, magnitude, recurrence, total);
    }

    /**
     * Log-scaled uplift for exceeding a rule's threshold.
     *
     * <p>Linear scaling would be wrong here: a transaction at 50x the threshold
     * is not fifty times more suspicious than one at 1x — beyond a point,
     * "very large" stops adding information. A logarithm gives meaningful
     * separation in the 1x-10x band where real discrimination happens, then
     * flattens, and the cap stops one enormous transaction from pinning every
     * alert to 100 and flattening the queue order.
     */
    private int magnitudeUplift(BigDecimal ratio) {
        if (ratio == null || ratio.compareTo(BigDecimal.ONE) <= 0) {
            return 0;
        }
        double log2 = Math.log(ratio.doubleValue()) / Math.log(2);
        return (int) Math.min(MAX_MAGNITUDE_UPLIFT, Math.round(5 * log2));
    }

    /**
     * Aggregates several alert scores into one case-level score using noisy-OR:
     * {@code 100 x (1 - product(1 - s/100))}.
     *
     * <p>Summation — the obvious choice — saturates immediately: three medium
     * alerts would total over 100 and clamp, so a case with three moderate
     * indicators would be indistinguishable from one with ten severe ones. The
     * top of the analyst queue would become a wall of 100s and the ordering
     * that business rule 7 exists to provide would be destroyed.
     *
     * <p>Noisy-OR is bounded below 100, strictly monotonic (more evidence never
     * lowers risk) and has a defensible reading: independent indicators
     * compounding, each reducing the probability that the activity is benign.
     */
    public int aggregate(Collection<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0;
        }
        double probabilityAllBenign = 1.0;
        for (Integer score : scores) {
            int s = clamp(score == null ? 0 : score);
            probabilityAllBenign *= (1.0 - (s / 100.0));
        }
        return clamp((int) Math.round(100 * (1 - probabilityAllBenign)));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(MAX_SCORE, value));
    }
}
