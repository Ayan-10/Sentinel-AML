package com.meridiantrust.sentinel.casemanagement.controller;

import com.meridiantrust.sentinel.transaction.model.Direction;

import com.meridiantrust.sentinel.alerting.mapper.AlertMapper;
import com.meridiantrust.sentinel.alerting.service.AlertService;
import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.casemanagement.dto.CaseDtos;
import com.meridiantrust.sentinel.casemanagement.service.CaseService;
import com.meridiantrust.sentinel.casemanagement.model.CaseFile;
import com.meridiantrust.sentinel.casemanagement.model.CaseStatus;
import com.meridiantrust.sentinel.common.dto.PageResponse;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.security.PiiMasker;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases")
@Tag(name = "Cases", description = "Analyst investigation workflow and disposition")
public class CaseController {

    private final CaseService caseService;
    private final AlertService alertService;
    private final AlertMapper alertMapper;
    private final AuditService auditService;
    private final CustomerRepository customerRepository;
    private final PiiMasker masker;

    public CaseController(CaseService caseService,
                          AlertService alertService,
                          AlertMapper alertMapper,
                          AuditService auditService,
                          CustomerRepository customerRepository,
                          PiiMasker masker) {
        this.caseService = caseService;
        this.alertService = alertService;
        this.alertMapper = alertMapper;
        this.auditService = auditService;
        this.customerRepository = customerRepository;
        this.masker = masker;
    }

    @PostMapping
    @Operation(summary = "Open a case over one or more alerts")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Case created"),
            @ApiResponse(responseCode = "400", description = "No alerts supplied, or alerts span multiple customers"),
            @ApiResponse(responseCode = "404", description = "One of the alert references does not exist")
    })
    public ResponseEntity<CaseDtos.Detail> open(@Valid @RequestBody CaseDtos.OpenRequest request) {
        CaseFile created = caseService.open(request.customerId(), request.title(),
                request.alertRefs(), request.narrative());
        return ResponseEntity
                .created(URI.create("/api/v1/cases/" + created.getCaseRef()))
                .body(toDetail(created));
    }

    @GetMapping
    @Operation(summary = "Case queue", description = "Sorted by aggregate risk score descending by default.")
    public PageResponse<CaseDtos.Summary> queue(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "aggregateRiskScore") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = Sort.by("asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        return PageResponse.from(
                caseService.search(status, assignedTo, customerId,
                        PageRequest.of(page, Math.min(size, 200), sort)),
                this::toSummary);
    }

    @GetMapping("/{caseRef}")
    @Operation(summary = "Case detail with member alerts and the immutable audit trail")
    public CaseDtos.Detail detail(@PathVariable String caseRef) {
        return toDetail(caseService.requireByRef(caseRef));
    }

    @PatchMapping("/{caseRef}/status")
    @Operation(summary = "Transition a case",
            description = """
                    Closing a case requires a disposition and reason, and cascades that
                    disposition onto every member alert — each cascade separately audited.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transition applied"),
            @ApiResponse(responseCode = "400", description = "Missing disposition or reason"),
            @ApiResponse(responseCode = "403", description = "Role does not permit this disposition"),
            @ApiResponse(responseCode = "422", description = "Illegal transition for the current state")
    })
    public CaseDtos.Detail transition(@PathVariable String caseRef,
                                      @Valid @RequestBody CaseDtos.TransitionRequest request) {
        return toDetail(caseService.transition(caseRef, request.targetStatus(),
                request.disposition(), request.reason()));
    }

    @PatchMapping("/{caseRef}/assignee")
    @Operation(summary = "Reassign a case")
    public CaseDtos.Detail assign(@PathVariable String caseRef,
                                  @Valid @RequestBody CaseDtos.AssignRequest request) {
        return toDetail(caseService.assign(caseRef, request.assignee()));
    }

    // --- mapping -----------------------------------------------------------

    private CaseDtos.Summary toSummary(CaseFile c) {
        return new CaseDtos.Summary(
                c.getCaseRef(), c.getCustomerId(), c.getTitle(), c.getStatus().name(),
                c.getPriority().name(), c.getAggregateRiskScore(), c.getAssignedTo(),
                c.getDisposition() == null ? null : c.getDisposition().name(),
                c.getOpenedBy(), c.getOpenedAt(), c.getUpdatedAt(), c.getClosedAt(),
                alertService.byCase(c.getId()).size());
    }

    private CaseDtos.Detail toDetail(CaseFile c) {
        List<Alert> alerts = alertService.byCase(c.getId());
        String maskedName = customerRepository.findById(c.getCustomerId())
                .map(Customer::getFullName).map(masker::maskName).orElse("Unknown");

        List<CaseDtos.AuditEntry> trail = auditService
                .trailFor(AuditAction.ENTITY_CASE, c.getCaseRef(), PageRequest.of(0, 100))
                .map(a -> new CaseDtos.AuditEntry(a.getAction(), a.getActor(),
                        a.getFromState(), a.getToState(), a.getDetails(), a.getOccurredAt()))
                .getContent();

        return new CaseDtos.Detail(
                c.getCaseRef(), c.getCustomerId(), maskedName, c.getTitle(),
                c.getStatus().name(), c.getPriority().name(), c.getAggregateRiskScore(),
                c.getAssignedTo(), c.getNarrative(),
                c.getDisposition() == null ? null : c.getDisposition().name(),
                c.getDispositionReason(), c.getOpenedBy(), c.getOpenedAt(),
                c.getUpdatedAt(), c.getClosedAt(),
                alerts.stream().map(alertMapper::toSummary).toList(),
                trail);
    }
}
