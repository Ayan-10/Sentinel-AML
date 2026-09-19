package com.meridiantrust.sentinel.alerting.dto;

import com.meridiantrust.sentinel.alerting.model.Alert;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Full alert view: the explanation, the supporting evidence, and how the score was derived. */
@Schema(description = "Alert detail with evidence transactions and risk score breakdown.")
public record AlertDetailDto(
        String alertRef,
        String customerId,
        String customerName,
        String accountId,
        String ruleCode,
        String typology,
        int riskScore,
        String severity,
        String status,
        @Schema(description = "Human-readable justification generated from the evidence")
        String explanation,
        @Schema(description = "How the risk score was composed")
        Map<String, Integer> scoreBreakdown,
        List<EvidenceTransactionDto> evidence,
        BigDecimal evidenceAmountBase,
        int triggerCount,
        String disposition,
        String dispositionReason,
        @Schema(description = "Analyst who disposed the alert (business rule 6)")
        String disposedBy,
        Instant disposedAt,
        String caseRef,
        Instant firstDetectedAt,
        Instant lastDetectedAt) {

    public record EvidenceTransactionDto(
            String transactionId,
            LocalDateTime timestamp,
            String direction,
            BigDecimal amount,
            String currency,
            BigDecimal amountBase,
            String channel,
            String counterpartyName,
            String counterpartyCountry) {}
}
