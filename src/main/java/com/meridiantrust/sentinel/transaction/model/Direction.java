package com.meridiantrust.sentinel.transaction.model;


/** Money flow relative to the monitored account. */
public enum Direction {
    /** Funds arriving — a deposit or inbound transfer. */
    CREDIT,
    /** Funds leaving — a withdrawal or outbound transfer. */
    DEBIT;

    public static Direction parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("direction is required");
        }
        return switch (raw.trim().toUpperCase()) {
            case "CREDIT", "CR", "C", "DEPOSIT", "IN" -> CREDIT;
            case "DEBIT", "DR", "D", "WITHDRAWAL", "OUT" -> DEBIT;
            default -> throw new IllegalArgumentException("unknown direction: " + raw);
        };
    }
}
