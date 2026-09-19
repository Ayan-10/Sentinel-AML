package com.meridiantrust.sentinel.ingestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A record of one ingestion run.
 *
 * <p>The brief requires ingestion errors to be logged. A counter alone would
 * not be enough to act on, so each batch is persisted with its accepted and
 * rejected totals, and each rejected row is linked back to it via
 * {@link IngestionError} — giving an operator a complete, queryable account of
 * what was loaded and what was refused.
 */
@Entity
@Table(name = "ingestion_batch")
@Getter
@Setter
@NoArgsConstructor
public class IngestionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_ref", nullable = false, unique = true, length = 40)
    private String batchRef;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    private String source;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "accepted_records", nullable = false)
    private int acceptedRecords;

    @Column(name = "rejected_records", nullable = false)
    private int rejectedRecords;

    @Column(name = "alerts_generated", nullable = false)
    private int alertsGenerated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BatchStatus status = BatchStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** NFR (Performance): reported so the 2-minute target is demonstrable, not asserted. */
    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "initiated_by")
    private String initiatedBy;
}
