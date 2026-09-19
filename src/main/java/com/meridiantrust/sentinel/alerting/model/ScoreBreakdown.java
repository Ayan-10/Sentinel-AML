package com.meridiantrust.sentinel.alerting.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The components that produced an alert's risk score.
 *
 * <p>Business rule 7 requires a score; the brief requires explainability. A
 * bare number satisfies the first and fails the second — an analyst asked to
 * prioritise by a score they cannot interrogate will stop trusting it. So the
 * breakdown is retained and returned with the alert, making the score auditable
 * in the same way the trigger is.
 */
public record ScoreBreakdown(
        int ruleWeight,
        int jurisdictionUplift,
        int customerRiskUplift,
        int pepUplift,
        int magnitudeUplift,
        int recurrenceUplift,
        int total) {

    /** Ordered for display — highest-level contributor first. */
    public Map<String, Integer> asMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("ruleWeight", ruleWeight);
        map.put("jurisdictionUplift", jurisdictionUplift);
        map.put("customerRiskUplift", customerRiskUplift);
        map.put("pepUplift", pepUplift);
        map.put("magnitudeUplift", magnitudeUplift);
        map.put("recurrenceUplift", recurrenceUplift);
        map.put("total", total);
        return map;
    }
}
