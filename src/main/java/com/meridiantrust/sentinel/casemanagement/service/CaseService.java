package com.meridiantrust.sentinel.casemanagement.service;

import com.meridiantrust.sentinel.alerting.service.AlertService;
import com.meridiantrust.sentinel.alerting.service.RiskScoringService;
import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.alerting.model.AlertStatus;
import com.meridiantrust.sentinel.alerting.model.Disposition;
import com.meridiantrust.sentinel.casemanagement.model.*;
import com.meridiantrust.sentinel.casemanagement.repository.*;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.common.security.CurrentUser;
import com.meridiantrust.sentinel.common.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Case management workflow — the analyst-facing half of the system.
 *
 * <p>All state-transition rules live here rather than in the controller. The
 * distinction matters: a controller is one entry point among several, while
 * this service is the only way into the domain. Putting the rules here means
 * they cannot be bypassed by adding an endpoint, and they are testable without
 * HTTP.
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CaseRepository repository;
    private final AlertService alertService;
    private final RiskScoringService scoringService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public CaseService(CaseRepository repository,
                       AlertService alertService,
                       RiskScoringService scoringService,
                       AuditService auditService,
                       CurrentUser currentUser) {
        this.repository = repository;
        this.alertService = alertService;
        this.scoringService = scoringService;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ANALYST)
    public CaseFile open(String customerId, String title, List<String> alertRefs, String narrative) {
        if (alertRefs == null || alertRefs.isEmpty()) {
            throw new ApiException.Validation("A case must be opened against at least one alert.");
        }

        List<Alert> alerts = alertRefs.stream().map(alertService::requireByRef).toList();

        // A case is a customer-level investigation. Alerts from different
        // customers in one case would make the narrative and any resulting SAR
        // incoherent, so the invariant is enforced rather than assumed.
        boolean mismatched = alerts.stream().anyMatch(a -> !a.getCustomerId().equals(customerId));
        if (mismatched) {
            throw new ApiException.Validation(
                    "All alerts in a case must belong to customer " + customerId);
        }

        int aggregate = scoringService.aggregate(alerts.stream().map(Alert::getRiskScore).toList());

        CaseFile caseFile = new CaseFile();
        caseFile.setCaseRef(generateRef());
        caseFile.setCustomerId(customerId);
        caseFile.setTitle(title == null || title.isBlank()
                ? "Investigation — " + customerId : title);
        caseFile.setStatus(CaseStatus.NEW);
        caseFile.setAggregateRiskScore(aggregate);
        caseFile.setPriority(Priority.fromScore(aggregate));
        caseFile.setNarrative(narrative);
        caseFile.setOpenedBy(currentUser.username());
        caseFile.setAssignedTo(currentUser.username());

        CaseFile saved = repository.save(caseFile);
        alertService.linkToCase(alertRefs, saved.getId(), saved.getCaseRef());

        auditService.record(AuditAction.ENTITY_CASE, saved.getCaseRef(), AuditAction.CASE_OPENED,
                null, saved.getStatus().name(),
                "Opened over %d alert(s), aggregate score %d".formatted(alerts.size(), aggregate));
        log.info("Case {} opened for customer {} with aggregate score {}",
                saved.getCaseRef(), customerId, aggregate);
        return saved;
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ANALYST)
    public CaseFile transition(String caseRef, CaseStatus target,
                               Disposition disposition, String reason) {
        CaseFile caseFile = requireByRef(caseRef);
        CaseStatus from = caseFile.getStatus();

        if (from == target) {
            return caseFile;
        }
        if (!from.canTransitionTo(target)) {
            throw new ApiException.IllegalTransition(
                    "Case %s cannot move from %s to %s".formatted(caseRef, from, target));
        }

        if (target == CaseStatus.CLOSED) {
            closeWithDisposition(caseFile, disposition, reason);
        }

        caseFile.setStatus(target);
        caseFile.setUpdatedAt(Instant.now());
        CaseFile saved = repository.save(caseFile);

        auditService.record(AuditAction.ENTITY_CASE, caseRef, AuditAction.CASE_STATUS_CHANGED,
                from.name(), target.name(),
                disposition == null ? null : "Disposition: %s — %s".formatted(disposition, reason));
        return saved;
    }

    /**
     * Business rule 6, applied at case level: a case cannot be closed without a
     * disposition and reason, and closing cascades that decision onto every
     * member alert — each cascade individually audited, so the trail shows why
     * each alert reached its end state.
     */
    private void closeWithDisposition(CaseFile caseFile, Disposition disposition, String reason) {
        if (disposition == null) {
            throw new ApiException.Validation("A disposition is required to close a case.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException.Validation("A disposition reason is required to close a case.");
        }
        if (disposition.requiresSeniorApproval() && !currentUser.hasRole(Roles.SENIOR_ANALYST)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "insufficient-role",
                    "Disposition %s requires the %s role.".formatted(disposition, Roles.SENIOR_ANALYST));
        }

        caseFile.setDisposition(disposition);
        caseFile.setDispositionReason(reason);
        caseFile.setClosedAt(Instant.now());

        for (Alert alert : alertService.byCase(caseFile.getId())) {
            if (alert.getStatus() != AlertStatus.CLOSED) {
                alertService.transition(alert.getAlertRef(), AlertStatus.CLOSED, disposition,
                        "Closed via case %s: %s".formatted(caseFile.getCaseRef(), reason));
            }
        }

        auditService.record(AuditAction.ENTITY_CASE, caseFile.getCaseRef(), AuditAction.CASE_DISPOSED,
                "Disposition %s by %s — %s".formatted(disposition, currentUser.username(), reason));
    }

    @Transactional
    @PreAuthorize(Roles.HAS_ANALYST)
    public CaseFile assign(String caseRef, String assignee) {
        CaseFile caseFile = requireByRef(caseRef);
        String previous = caseFile.getAssignedTo();
        caseFile.setAssignedTo(assignee);
        caseFile.setUpdatedAt(Instant.now());
        CaseFile saved = repository.save(caseFile);
        auditService.record(AuditAction.ENTITY_CASE, caseRef, AuditAction.CASE_ASSIGNED,
                previous, assignee, "Reassigned");
        return saved;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public CaseFile requireByRef(String caseRef) {
        return repository.findByCaseRef(caseRef)
                .orElseThrow(() -> new ApiException.NotFound("Case", caseRef));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public Page<CaseFile> search(CaseStatus status, String assignedTo, String customerId, Pageable pageable) {
        return repository.search(status, assignedTo, customerId, pageable);
    }

    private String generateRef() {
        return "CASE-%s-%s".formatted(
                LocalDate.now().format(REF_DATE),
                UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }
}
