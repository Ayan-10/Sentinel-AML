package com.meridiantrust.sentinel.alerting.controller;

import com.meridiantrust.sentinel.alerting.mapper.AlertMapper;
import com.meridiantrust.sentinel.transaction.model.Direction;

import com.meridiantrust.sentinel.alerting.dto.AlertDetailDto;
import com.meridiantrust.sentinel.alerting.dto.AlertSummaryDto;
import com.meridiantrust.sentinel.alerting.dto.AlertTransitionRequest;
import com.meridiantrust.sentinel.alerting.service.AlertService;
import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.alerting.model.AlertStatus;
import com.meridiantrust.sentinel.common.dto.PageResponse;
import com.meridiantrust.sentinel.common.model.Severity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The analyst alert queue.
 *
 * <p>Controllers here do HTTP and nothing else: bind, delegate, map, return.
 * No business logic, no transactions, no try/catch — failures are translated
 * centrally by {@code GlobalExceptionHandler}, and authorisation is declared on
 * the service as well as on the URL.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Risk-scored alert queue and analyst dispositions")
public class AlertController {

    private final AlertService alertService;
    private final AlertMapper mapper;

    public AlertController(AlertService alertService, AlertMapper mapper) {
        this.alertService = alertService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Alert queue",
            description = """
                    Returns alerts with customer PII masked (business rule 8).
                    Sorted by risk score descending by default so the highest-risk
                    alerts reach the top of the queue (business rule 7).
                    """)
    public PageResponse<AlertSummaryDto> queue(
            @Parameter(description = "OPEN, IN_REVIEW, ESCALATED or CLOSED")
            @RequestParam(required = false) AlertStatus status,
            @Parameter(description = "LOW, MEDIUM, HIGH or CRITICAL")
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String customerId,
            @Parameter(description = "Only alerts at or above this risk score")
            @RequestParam(required = false) Integer minScore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "Property to sort by, e.g. riskScore or lastDetectedAt")
            @RequestParam(defaultValue = "riskScore") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = Sort.by("asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Page<Alert> alerts = alertService.search(status, severity, ruleCode, customerId, minScore,
                PageRequest.of(page, Math.min(size, 200), sort));
        return PageResponse.from(alerts, mapper::toSummary);
    }

    @GetMapping("/{alertRef}")
    @Operation(summary = "Alert detail",
            description = "Full explanation, supporting evidence transactions and risk score breakdown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alert found"),
            @ApiResponse(responseCode = "404", description = "No such alert")
    })
    public AlertDetailDto detail(@PathVariable String alertRef) {
        return mapper.toDetail(alertService.requireByRef(alertRef));
    }

    @PatchMapping("/{alertRef}/status")
    @Operation(summary = "Transition an alert",
            description = """
                    Moves an alert through its lifecycle. Closing requires a disposition
                    and a reason, both retained permanently with the analyst's identity —
                    alerts are never deleted (business rule 6).

                    ESCALATED_TO_SAR requires the SENIOR_ANALYST role.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transition applied"),
            @ApiResponse(responseCode = "400", description = "Missing disposition or reason"),
            @ApiResponse(responseCode = "403", description = "Role does not permit this disposition"),
            @ApiResponse(responseCode = "409", description = "Concurrently modified — reload and retry"),
            @ApiResponse(responseCode = "422", description = "Illegal transition for the current state")
    })
    public ResponseEntity<AlertDetailDto> transition(@PathVariable String alertRef,
                                                     @Valid @RequestBody AlertTransitionRequest request) {
        Alert updated = alertService.transition(alertRef, request.targetStatus(),
                request.disposition(), request.reason());
        return ResponseEntity.ok(mapper.toDetail(updated));
    }
}
