package com.meridiantrust.sentinel.common.model;


/**
 * Alert severity band. Derived from the numeric risk score rather than set
 * independently, so the two can never disagree.
 */
public enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** Business rule 7: banding of the 0-100 risk score. */
    public static Severity fromScore(int score) {
        if (score >= 80) return CRITICAL;
        if (score >= 60) return HIGH;
        if (score >= 40) return MEDIUM;
        return LOW;
    }
}
