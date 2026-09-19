package com.meridiantrust.sentinel.common.model;


/** Customer / account inherent risk classification from KYC onboarding. */
public enum RiskRating {
    LOW(0),
    MEDIUM(7),
    HIGH(15);

    private final int scoreUplift;

    RiskRating(int scoreUplift) {
        this.scoreUplift = scoreUplift;
    }

    /** Contribution this rating makes to an alert's risk score (business rule 7). */
    public int scoreUplift() {
        return scoreUplift;
    }

    public static RiskRating fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return LOW;
        }
    }
}
