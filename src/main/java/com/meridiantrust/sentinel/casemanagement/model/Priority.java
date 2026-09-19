package com.meridiantrust.sentinel.casemanagement.model;

/** Case triage priority, derived from the aggregate risk score. */
public enum Priority {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static Priority fromScore(int aggregateScore) {
        if (aggregateScore >= 80) return CRITICAL;
        if (aggregateScore >= 60) return HIGH;
        if (aggregateScore >= 40) return MEDIUM;
        return LOW;
    }
}
