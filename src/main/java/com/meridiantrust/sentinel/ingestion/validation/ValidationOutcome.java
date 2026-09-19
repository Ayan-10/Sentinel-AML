package com.meridiantrust.sentinel.ingestion.validation;

/** Result of one validator in the chain. */
public record ValidationOutcome(boolean valid, String errorType, String message) {

    private static final ValidationOutcome OK = new ValidationOutcome(true, null, null);

    public static ValidationOutcome ok() {
        return OK;
    }

    public static ValidationOutcome reject(String errorType, String message) {
        return new ValidationOutcome(false, errorType, message);
    }

    public static final String STRUCTURAL = "STRUCTURAL";
    public static final String REFERENTIAL = "REFERENTIAL_INTEGRITY";
    public static final String BUSINESS = "BUSINESS_RULE";
}
