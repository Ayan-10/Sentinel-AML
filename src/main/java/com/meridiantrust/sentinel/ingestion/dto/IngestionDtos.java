package com.meridiantrust.sentinel.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class IngestionDtos {

    private IngestionDtos() {}

    /**
     * A single inbound transaction on the streaming endpoint.
     *
     * <p>Bean validation here catches the structurally obvious so a malformed
     * request never reaches the service. The deeper checks — referential
     * integrity, currency support, duplicate detection — stay in the validation
     * chain, because they need context that an annotation cannot see.
     */
    @Schema(description = "A single transaction for real-time evaluation.")
    public record SingleTransactionRequest(
            @NotBlank(message = "transactionId is required")
            @Size(max = 40) String transactionId,

            @NotBlank(message = "accountId is required")
            @Size(max = 32) String accountId,

            @Schema(description = "Optional; derived from the account when omitted")
            String customerId,

            @NotBlank(message = "txnTimestamp is required")
            @Schema(example = "2026-09-19T14:32:00") String txnTimestamp,

            @NotBlank(message = "direction is required")
            @Pattern(regexp = "(?i)CREDIT|DEBIT", message = "direction must be CREDIT or DEBIT")
            String direction,

            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be greater than zero")
            BigDecimal amount,

            @NotBlank(message = "currency is required")
            @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO-4217 code")
            String currency,

            String channel,
            String txnType,
            String counterpartyName,
            String counterpartyAccount,
            String counterpartyBank,

            @Pattern(regexp = "|[A-Za-z]{2}", message = "counterpartyCountry must be a 2-letter ISO code")
            String counterpartyCountry,

            String description) {}

    @Schema(description = "Outcome of an ingestion run.")
    public record IngestionResponse(
            String batchRef,
            String entityType,
            int totalRecords,
            int acceptedRecords,
            int rejectedRecords,
            int alertsGenerated,
            @Schema(description = "Wall-clock duration — evidences the bulk performance target")
            long durationMs,
            @Schema(description = "Every rejected row with its reason, so failures are actionable")
            List<RejectionDto> rejections) {}

    public record RejectionDto(int rowNumber, String recordKey, String errorType, String message) {}

    @Schema(description = "Result of evaluating a single streamed transaction.")
    public record StreamingResponse(
            String transactionId,
            boolean accepted,
            int rulesTriggered,
            int alertsCreated,
            @Schema(description = "Round-trip detection latency in milliseconds")
            long detectionMs,
            Instant evaluatedAt) {}

    @Schema(description = "Ingestion batch summary with rejected-row detail.")
    public record BatchSummary(
            String batchRef,
            String entityType,
            String source,
            int totalRecords,
            int acceptedRecords,
            int rejectedRecords,
            int alertsGenerated,
            String status,
            Instant startedAt,
            Instant finishedAt,
            Long durationMs,
            String initiatedBy,
            List<RejectionDto> rejections) {}
}
