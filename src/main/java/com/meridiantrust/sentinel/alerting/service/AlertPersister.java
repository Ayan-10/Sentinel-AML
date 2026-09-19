package com.meridiantrust.sentinel.alerting.service;

import com.meridiantrust.sentinel.alerting.model.ScoreBreakdown;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.alerting.repository.AlertRepository;
import com.meridiantrust.sentinel.common.audit.model.AuditAction;
import com.meridiantrust.sentinel.common.audit.service.AuditService;
import com.meridiantrust.sentinel.common.model.Severity;
import com.meridiantrust.sentinel.customer.model.Customer;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional boundary for a single alert write.
 *
 * <p>Each method runs in its own transaction ({@code REQUIRES_NEW}). This is
 * not incidental — it is what makes the deduplication race recoverable. When
 * two threads detect the same pattern concurrently, the loser's {@code INSERT}
 * violates the unique constraint on {@code dedup_key}. In JPA, a constraint
 * violation poisons the surrounding transaction: it is marked rollback-only,
 * and any further work in it fails. By isolating the insert attempt in its own
 * transaction, the caller can catch the violation and merge into the winner's
 * alert on a clean transaction instead of losing the whole chunk.
 *
 * <p>NFR (Concurrency): "no duplicate or lost alerts" — the constraint prevents
 * the duplicate, and this isolation prevents the loss.
 */
@Component
public class AlertPersister {

    private static final Logger log = LoggerFactory.getLogger(AlertPersister.class);
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AlertRepository repository;
    private final RiskScoringService scoringService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AlertPersister(AlertRepository repository,
                          RiskScoringService scoringService,
                          AuditService auditService,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.scoringService = scoringService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Alert> findByDedupKey(String dedupKey) {
        return repository.findByDedupKey(dedupKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alert insertNew(RuleHit hit, int ruleWeight, Customer customer) {
        ScoreBreakdown breakdown = scoringService.score(hit, ruleWeight, customer, 0);

        Alert alert = new Alert();
        alert.setAlertRef(generateRef());
        alert.setCustomerId(hit.customerId());
        alert.setAccountId(hit.accountId());
        alert.setRuleCode(hit.ruleCode());
        alert.setTypology(hit.typology());
        alert.setRiskScore(breakdown.total());
        alert.setSeverity(Severity.fromScore(breakdown.total()));
        alert.setExplanation(hit.explanation());
        alert.setEvidenceTxnIds(String.join(",", hit.evidenceTransactionIds()));
        alert.setEvidenceAmountBase(hit.evidenceAmountBase());
        alert.setDedupKey(hit.dedupKey());
        alert.setScoreBreakdown(writeBreakdown(breakdown));
        alert.setFirstDetectedAt(Instant.now());
        alert.setLastDetectedAt(Instant.now());

        Alert saved = repository.saveAndFlush(alert);
        auditService.record(AuditAction.ENTITY_ALERT, saved.getAlertRef(), AuditAction.ALERT_CREATED,
                null, saved.getStatus().name(),
                "Rule %s fired with score %d".formatted(saved.getRuleCode(), saved.getRiskScore()));
        return saved;
    }

    /**
     * Folds a repeat detection into the existing alert rather than raising a
     * second one — the brief's "one customer doesn't generate 50 redundant
     * alerts for the same underlying pattern".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Alert> mergeExisting(RuleHit hit, int ruleWeight, Customer customer) {
        Optional<Alert> found = repository.findByDedupKey(hit.dedupKey());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Alert alert = found.get();

        // A disposed alert is evidence of a decision already taken. Re-scoring
        // it would silently reopen settled work and destroy the audit
        // narrative, so repeat detections after disposition are ignored here
        // (a genuinely new pattern produces a different dedup key, and so a
        // new alert).
        if (alert.isDisposed()) {
            log.debug("Repeat detection for disposed alert {} — not re-scoring", alert.getAlertRef());
            return Optional.of(alert);
        }

        ScoreBreakdown breakdown = scoringService.score(hit, ruleWeight, customer, alert.getTriggerCount());
        alert.mergeRepeatDetection(hit.evidenceTransactionIds(), hit.evidenceAmountBase(), breakdown.total());
        alert.setScoreBreakdown(writeBreakdown(breakdown));
        Alert saved = repository.saveAndFlush(alert);

        auditService.record(AuditAction.ENTITY_ALERT, saved.getAlertRef(), AuditAction.ALERT_MERGED,
                "Repeat detection folded in; trigger count now %d, score %d"
                        .formatted(saved.getTriggerCount(), saved.getRiskScore()));
        return Optional.of(saved);
    }

    private String generateRef() {
        return "ALT-%s-%s".formatted(
                LocalDate.now().format(REF_DATE),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private String writeBreakdown(ScoreBreakdown breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown.asMap());
        } catch (Exception ex) {
            log.warn("Could not serialise score breakdown: {}", ex.getMessage());
            return null;
        }
    }
}
