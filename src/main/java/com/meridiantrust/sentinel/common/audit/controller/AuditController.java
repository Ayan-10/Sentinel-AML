package com.meridiantrust.sentinel.common.audit.controller;

import com.meridiantrust.sentinel.common.audit.model.AuditLog;
import com.meridiantrust.sentinel.common.audit.service.AuditService;

import com.meridiantrust.sentinel.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Read-only view of the immutable audit trail.
 *
 * <p>There is deliberately no write endpoint and no delete endpoint. Audit
 * records are produced as a side effect of the state transitions they describe;
 * exposing a way to author them directly would make the trail forgeable and
 * therefore worthless.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Immutable trail of all alert and case state transitions")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Audit trail",
            description = """
                    Every alert and case state transition with its actor and timestamp.
                    Filter by entityType (ALERT, CASE, RULE, REFERENCE, INGESTION) and
                    optionally by a specific entityId.
                    """)
    public PageResponse<AuditEntryDto> trail(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 500));
        var results = (entityId != null && !entityId.isBlank())
                ? auditService.trailFor(entityType, entityId, pageable)
                : auditService.trail(entityType, pageable);

        return PageResponse.from(results, AuditEntryDto::from);
    }

    public record AuditEntryDto(Long id, String entityType, String entityId, String action,
                                String actor, String actorRoles, String fromState,
                                String toState, String details, Instant occurredAt) {

        static AuditEntryDto from(AuditLog a) {
            return new AuditEntryDto(a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(),
                    a.getActor(), a.getActorRoles(), a.getFromState(), a.getToState(),
                    a.getDetails(), a.getOccurredAt());
        }
    }
}
