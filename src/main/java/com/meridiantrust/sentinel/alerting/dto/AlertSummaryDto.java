package com.meridiantrust.sentinel.alerting.dto;

import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.customer.model.Customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alert queue row — business rule 8's masked list view.
 *
 * <p>This DTO deliberately has no unmasked name or identifier field. Masking is
 * applied when the record is constructed, so there is nothing here for a
 * serialisation mistake, a logging statement or a future field addition to
 * leak. A single DTO with an {@code unmasked} boolean would fail open; this
 * fails closed.
 */
@Schema(description = "Alert queue row. Customer PII is masked; use the detail endpoint for full values.")
public record AlertSummaryDto(
        @Schema(example = "ALT-20260919-A1B2C3D4") String alertRef,
        @Schema(example = "CUST_00042") String customerId,
        @Schema(description = "Masked customer name", example = "K****** S*****") String customerNameMasked,
        @Schema(description = "Masked account number", example = "******0123") String accountMasked,
        String ruleCode,
        String typology,
        @Schema(description = "0-100 weighted risk score (business rule 7)", example = "78") int riskScore,
        String severity,
        String status,
        BigDecimal evidenceAmountBase,
        int evidenceCount,
        int triggerCount,
        String disposition,
        Instant firstDetectedAt,
        Instant lastDetectedAt) {
}
