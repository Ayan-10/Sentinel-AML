package com.meridiantrust.sentinel.ingestion.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single rejected row.
 *
 * <p>The raw record is retained deliberately. A rejection message without the
 * offending data is not actionable — an operator needs to see what was sent in
 * order to fix and resubmit it.
 */
@Entity
@Table(name = "ingestion_error")
@Getter
@Setter
@NoArgsConstructor
public class IngestionError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "record_key", length = 64)
    private String recordKey;

    @Column(name = "error_type", nullable = false, length = 48)
    private String errorType;

    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    @Column(name = "raw_record", columnDefinition = "TEXT")
    private String rawRecord;

    public IngestionError(Long batchId, int rowNumber, String recordKey,
                          String errorType, String errorMessage, String rawRecord) {
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.recordKey = recordKey;
        this.errorType = errorType;
        this.errorMessage = truncate(errorMessage, 500);
        this.rawRecord = rawRecord;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
