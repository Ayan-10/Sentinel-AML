package com.meridiantrust.sentinel.alerting.model;

import com.meridiantrust.sentinel.common.model.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A risk-scored, explainable detection outcome.
 *
 * <p>Append-and-amend only. Business rule 6: alerts are never silently deleted;
 * clearing one is a state transition that records disposition, reason and
 * analyst identity, mirrored into the immutable audit log.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_ref", nullable = false, unique = true, length = 40)
    private String alertRef;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private String typology;

    /** Business rule 7: 0-100 weighted score driving analyst queue order. */
    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AlertStatus status = AlertStatus.OPEN;

    /** The "explain WHY" requirement — generated from evidence, never canned. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "evidence_txn_ids", nullable = false, columnDefinition = "TEXT")
    private String evidenceTxnIds;

    @Column(name = "evidence_amount_base")
    private BigDecimal evidenceAmountBase;

    /** JSON breakdown of how riskScore was derived — makes the score auditable. */
    @Column(name = "score_breakdown", columnDefinition = "TEXT")
    private String scoreBreakdown;

    /**
     * Deterministic identity of the underlying pattern, unique-constrained in
     * the database. That constraint — not application-level checking — is what
     * makes concurrent detection safe against duplicate alerts.
     */
    @Column(name = "dedup_key", nullable = false, unique = true, length = 200)
    private String dedupKey;

    @Column(name = "trigger_count", nullable = false)
    private int triggerCount = 1;

    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt = Instant.now();

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Disposition disposition;

    @Column(name = "disposition_reason", columnDefinition = "TEXT")
    private String dispositionReason;

    @Column(name = "disposed_by")
    private String disposedBy;

    @Column(name = "disposed_at")
    private Instant disposedAt;

    @Column(name = "case_id")
    private Long caseId;

    /** NFR (Concurrency): prevents one analyst's edit silently overwriting another's. */
    @Version
    private Integer version;

    // --- Behaviour ---------------------------------------------------------

    public List<String> evidenceList() {
        if (evidenceTxnIds == null || evidenceTxnIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(evidenceTxnIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Folds a repeat detection of the same pattern into this alert.
     *
     * <p>Evidence is unioned rather than replaced, so the alert accumulates the
     * full picture across detection runs instead of showing only the most
     * recent slice.
     */
    public void mergeRepeatDetection(List<String> newEvidence, BigDecimal newAmountBase, int recomputedScore) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(evidenceList());
        if (newEvidence != null) {
            merged.addAll(newEvidence);
        }
        this.evidenceTxnIds = String.join(",", new ArrayList<>(merged));
        this.triggerCount += 1;
        this.lastDetectedAt = Instant.now();
        if (newAmountBase != null && (evidenceAmountBase == null
                || newAmountBase.compareTo(evidenceAmountBase) > 0)) {
            this.evidenceAmountBase = newAmountBase;
        }
        this.riskScore = recomputedScore;
        this.severity = Severity.fromScore(recomputedScore);
    }

    public boolean isDisposed() {
        return disposition != null;
    }
}
