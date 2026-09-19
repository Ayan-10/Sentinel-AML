package com.meridiantrust.sentinel.alerting.service;

import com.meridiantrust.sentinel.alerting.model.*;
import com.meridiantrust.sentinel.alerting.repository.*;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.model.Severity;
import com.meridiantrust.sentinel.common.error.ApiException;
import com.meridiantrust.sentinel.common.security.CurrentUser;
import com.meridiantrust.sentinel.common.security.Roles;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alert lifecycle: creation from detection hits, and analyst-driven transitions.
 *
 * <p>Authorisation is declared here, on the service, rather than only on the
 * controller. A {@code @PreAuthorize} at this layer keeps holding when the
 * method is called from a scheduler, a future message listener, or a controller
 * somebody adds later and forgets to secure — which is exactly the
 * "enforced at the API layer, not just the UI" requirement, taken one layer
 * deeper for safety.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository repository;
    private final AlertPersister persister;
    private final RuleConfigProvider configProvider;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AlertService(AlertRepository repository,
                        AlertPersister persister,
                        RuleConfigProvider configProvider,
                        AuditService auditService,
                        CurrentUser currentUser) {
        this.repository = repository;
        this.persister = persister;
        this.configProvider = configProvider;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    // --- Creation from detection -------------------------------------------

    /**
     * Persists detection hits, deduplicating against existing alerts.
     *
     * <p>The sequence — check, insert, catch, merge — is the concurrency
     * guarantee. The initial lookup handles the common case cheaply; the
     * catch handles the race where another thread inserted the same dedup key
     * between our lookup and our insert. Without the catch, concurrent streams
     * would surface the constraint violation as a failed batch; without the
     * constraint, they would produce duplicate alerts. Both are required.
     *
     * @return the number of alerts newly created (merges are not counted)
     */
    public int persistHits(List<RuleHit> hits, Map<String, Customer> customers) {
        int created = 0;
        for (RuleHit hit : hits) {
            Customer customer = customers.get(hit.customerId());
            int weight = configProvider.weightOf(hit.ruleCode());

            Optional<Alert> existing = persister.findByDedupKey(hit.dedupKey());
            if (existing.isPresent()) {
                persister.mergeExisting(hit, weight, customer);
                continue;
            }
            try {
                persister.insertNew(hit, weight, customer);
                created++;
            } catch (DataIntegrityViolationException race) {
                // Another thread won the insert for this pattern. Expected
                // under concurrent ingestion — fold into theirs.
                log.debug("Dedup race on key {} — merging into existing alert", hit.dedupKey());
                persister.mergeExisting(hit, weight, customer);
            }
        }
        return created;
    }

    // --- Queue and retrieval ----------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public Page<Alert> search(AlertStatus status, Severity severity, String ruleCode,
                              String customerId, Integer minScore, Pageable pageable) {
        return repository.search(status, severity, ruleCode, customerId, minScore, pageable);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public Alert requireByRef(String alertRef) {
        return repository.findByAlertRef(alertRef)
                .orElseThrow(() -> new ApiException.NotFound("Alert", alertRef));
    }

    // --- Analyst transitions ------------------------------------------------

    /**
     * Moves an alert through its lifecycle.
     *
     * <p>Business rule 6: closing an alert requires a disposition and a
     * non-blank reason, and both — with the analyst's identity — are retained
     * permanently. Nothing here deletes.
     */
    @Transactional
    @PreAuthorize(Roles.HAS_ANALYST)
    public Alert transition(String alertRef, AlertStatus target,
                            Disposition disposition, String reason) {
        Alert alert = repository.findByAlertRef(alertRef)
                .orElseThrow(() -> new ApiException.NotFound("Alert", alertRef));

        AlertStatus from = alert.getStatus();
        if (from == target) {
            return alert;
        }
        if (!from.canTransitionTo(target)) {
            throw new ApiException.IllegalTransition(
                    "Alert %s cannot move from %s to %s".formatted(alertRef, from, target));
        }

        if (target == AlertStatus.CLOSED) {
            applyDisposition(alert, disposition, reason);
        }

        alert.setStatus(target);
        Alert saved = repository.save(alert);

        auditService.record(AuditAction.ENTITY_ALERT, alertRef, AuditAction.ALERT_STATUS_CHANGED,
                from.name(), target.name(),
                disposition == null ? null : "Disposition: %s — %s".formatted(disposition, reason));
        return saved;
    }

    private void applyDisposition(Alert alert, Disposition disposition, String reason) {
        if (disposition == null) {
            throw new ApiException.Validation(
                    "A disposition is required to close an alert (business rule 6).");
        }
        if (reason == null || reason.isBlank()) {
            throw new ApiException.Validation(
                    "A disposition reason is required to close an alert (business rule 6).");
        }
        if (disposition.requiresSeniorApproval() && !currentUser.hasRole(Roles.SENIOR_ANALYST)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "insufficient-role",
                    "Disposition %s requires the %s role.".formatted(disposition, Roles.SENIOR_ANALYST));
        }
        alert.setDisposition(disposition);
        alert.setDispositionReason(reason);
        alert.setDisposedBy(currentUser.username());
        alert.setDisposedAt(Instant.now());
    }

    @Transactional
    public void linkToCase(List<String> alertRefs, Long caseId, String caseRef) {
        for (String ref : alertRefs) {
            Alert alert = repository.findByAlertRef(ref)
                    .orElseThrow(() -> new ApiException.NotFound("Alert", ref));
            alert.setCaseId(caseId);
            if (alert.getStatus() == AlertStatus.OPEN) {
                alert.setStatus(AlertStatus.IN_REVIEW);
            }
            repository.save(alert);
            auditService.record(AuditAction.ENTITY_ALERT, ref, AuditAction.ALERT_LINKED_TO_CASE,
                    "Linked to case " + caseRef);
        }
    }

    @Transactional(readOnly = true)
    public List<Alert> byCase(Long caseId) {
        return repository.findByCaseId(caseId);
    }

    @Transactional(readOnly = true)
    public List<Alert> byCustomer(String customerId) {
        return repository.findByCustomerIdOrderByRiskScoreDesc(customerId);
    }
}
