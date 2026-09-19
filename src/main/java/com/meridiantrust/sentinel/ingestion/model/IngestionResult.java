package com.meridiantrust.sentinel.ingestion.model;

import java.util.List;

/**
 * Summary of one ingestion run, returned to the caller.
 *
 * <p>Rejections are returned inline, not merely counted. "412 accepted, 8
 * rejected" tells an operator that something is wrong but not what to fix;
 * carrying the reasons back makes the response actionable in one round trip.
 */
public record IngestionResult(
        String batchRef,
        String entityType,
        int totalRecords,
        int acceptedRecords,
        int rejectedRecords,
        int alertsGenerated,
        long durationMs,
        List<RejectedRecord> rejections) {

    public record RejectedRecord(int rowNumber, String recordKey, String errorType, String message) {}

    public boolean hasRejections() {
        return rejectedRecords > 0;
    }
}
