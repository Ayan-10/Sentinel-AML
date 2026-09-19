package com.meridiantrust.sentinel.alerting.model;

import java.time.Instant;

/**
 * The slice of a disposed alert the productivity calculator needs.
 *
 * <p>A projection rather than the {@code Alert} entity, for the same reason the
 * SAR composer takes a projection: it keeps the calculation a pure function over
 * values, testable with no Spring context and no database.
 */
public record DispositionRecord(
        String ruleCode,
        Disposition disposition,
        Instant firstDetectedAt,
        Instant disposedAt) {
}
