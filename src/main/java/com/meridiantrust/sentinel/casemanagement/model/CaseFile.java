package com.meridiantrust.sentinel.casemanagement.model;


import com.meridiantrust.sentinel.alerting.model.Disposition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An analyst investigation grouping one or more alerts for a single customer.
 *
 * <p>Named {@code CaseFile} because {@code case} is a Java keyword; the table
 * remains {@code cases}.
 */
@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
public class CaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_ref", nullable = false, unique = true, length = 40)
    private String caseRef;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CaseStatus status = CaseStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority = Priority.MEDIUM;

    /** Noisy-OR combination of member alert scores — see RiskScoringService. */
    @Column(name = "aggregate_risk_score", nullable = false)
    private int aggregateRiskScore;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(columnDefinition = "TEXT")
    private String narrative;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Disposition disposition;

    @Column(name = "disposition_reason", columnDefinition = "TEXT")
    private String dispositionReason;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private Integer version;
}
