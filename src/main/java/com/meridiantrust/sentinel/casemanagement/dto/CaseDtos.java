package com.meridiantrust.sentinel.casemanagement.dto;

import com.meridiantrust.sentinel.alerting.model.Alert;

import com.meridiantrust.sentinel.alerting.dto.AlertSummaryDto;
import com.meridiantrust.sentinel.alerting.model.Disposition;
import com.meridiantrust.sentinel.casemanagement.model.CaseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CaseDtos {

    private CaseDtos() {}

    @Schema(description = "Open an investigation over one or more alerts for a single customer.")
    public record OpenRequest(
            @NotBlank(message = "customerId is required")
            String customerId,

            @Size(max = 200) String title,

            @NotEmpty(message = "at least one alertRef is required")
            @Schema(description = "Alert references to investigate together")
            List<String> alertRefs,

            @Size(max = 4000) String narrative) {}

    @Schema(description = "Case state transition. Closing requires a disposition and reason.")
    public record TransitionRequest(
            @NotNull(message = "targetStatus is required")
            CaseStatus targetStatus,
            Disposition disposition,
            @Size(max = 4000) String reason) {}

    @Schema(description = "Reassign a case to another analyst.")
    public record AssignRequest(
            @NotBlank(message = "assignee is required") String assignee) {}

    @Schema(description = "Case summary for the case queue.")
    public record Summary(
            String caseRef,
            String customerId,
            String title,
            String status,
            String priority,
            @Schema(description = "Noisy-OR combination of member alert scores")
            int aggregateRiskScore,
            String assignedTo,
            String disposition,
            String openedBy,
            Instant openedAt,
            Instant updatedAt,
            Instant closedAt,
            int alertCount) {}

    @Schema(description = "Full case view including member alerts and audit trail.")
    public record Detail(
            String caseRef,
            String customerId,
            String customerNameMasked,
            String title,
            String status,
            String priority,
            int aggregateRiskScore,
            String assignedTo,
            String narrative,
            String disposition,
            String dispositionReason,
            String openedBy,
            Instant openedAt,
            Instant updatedAt,
            Instant closedAt,
            List<AlertSummaryDto> alerts,
            List<AuditEntry> auditTrail) {}

    @Schema(description = "One immutable audit record.")
    public record AuditEntry(
            String action,
            String actor,
            String fromState,
            String toState,
            String details,
            Instant occurredAt) {}
}
