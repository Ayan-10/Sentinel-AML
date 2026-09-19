package com.meridiantrust.sentinel.detection.model;

/**
 * Outcome of a detection run, surfaced to the caller so the streaming path can
 * report inline whether a transaction alerted.
 */
public record DetectionResult(int transactionsEvaluated,
                              int hitsFound,
                              int alertsCreated,
                              long durationMs) {

    public static DetectionResult empty() {
        return new DetectionResult(0, 0, 0, 0);
    }
}
