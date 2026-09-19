package com.meridiantrust.sentinel.sar.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A draft Suspicious Activity Report assembled from a closed or escalated case.
 *
 * <p>This is a <em>draft</em>, deliberately. A filed SAR is a legal document; the
 * system's job is to remove the clerical burden of gathering subject details,
 * account details and the transaction schedule, and to turn the evidence the
 * detection engine already assembled into narrative form. A compliance officer
 * reviews, edits and files it — the platform never files on anyone's behalf.
 *
 * <p>Structured rather than a single blob of text so the same draft can be
 * rendered as a form, exported to a regulator's schema, or diffed against a
 * later version without re-parsing prose.
 */
@Schema(description = "A draft Suspicious Activity Report generated from a case's alerts and evidence.")
public record SarDraft(
        @Schema(example = "SAR-DRAFT-20260919-4F2A1C") String draftRef,
        @Schema(example = "CASE-20260919-1F5E98") String caseRef,
        Instant generatedAt,
        @Schema(description = "Analyst who generated the draft") String generatedBy,
        @Schema(description = "Institution filing the report") String filingInstitution,

        Subject subject,
        List<AccountSummary> accounts,
        ActivitySummary activity,

        @Schema(description = "Narrative in regulatory filing form, composed from the alert evidence")
        String narrative,

        @Schema(description = "Chronological schedule of the transactions cited as evidence")
        List<TransactionLine> transactions,

        @Schema(description = "Laundering typologies detected across the case's alerts")
        List<String> typologies,

        @Schema(description = "Alert references this draft is built from")
        List<String> sourceAlerts,

        String recommendedAction) {

    /**
     * Subject of the report — carries <em>unmasked</em> PII by necessity: a SAR
     * that masks the subject identifies nobody and is useless to a Financial
     * Intelligence Unit. This is precisely why the endpoint is restricted to
     * SENIOR_ANALYST and why generating a draft is written to the audit trail.
     */
    @Schema(description = "Subject of the report. Contains unmasked PII — SENIOR_ANALYST only.")
    public record Subject(
            String customerId,
            String fullName,
            String nationalId,
            LocalDate dateOfBirth,
            String address,
            String occupation,
            String customerSegment,
            String kycStatus,
            String riskRating,
            boolean politicallyExposed,
            LocalDate customerSince) {}

    public record AccountSummary(
            String accountId,
            String accountType,
            String currency,
            LocalDate openDate,
            String branch) {}

    @Schema(description = "Aggregate picture of the suspicious activity.")
    public record ActivitySummary(
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            int transactionCount,
            @Schema(description = "Total value of cited transactions, normalised to the base currency")
            BigDecimal totalValueBase,
            String baseCurrency,
            int alertCount,
            int highestRiskScore,
            String caseDisposition) {}

    @Schema(description = "One line of the transaction schedule.")
    public record TransactionLine(
            String transactionId,
            LocalDateTime timestamp,
            String accountId,
            String direction,
            BigDecimal amountBase,
            BigDecimal originalAmount,
            String originalCurrency,
            String channel,
            String counterpartyName,
            String counterpartyCountry,
            @Schema(description = "Alerts that cite this transaction as evidence")
            List<String> citedBy) {}
}
