package com.meridiantrust.sentinel.reference.model;

import com.meridiantrust.sentinel.common.model.Severity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Runtime-tunable configuration for one detection rule.
 *
 * <p>The brief requires thresholds and time windows to be changed without a
 * code redeployment. Storing them here — rather than in
 * {@code application.properties}, which is baked into the image — is what makes
 * that true: a {@code PATCH} to the admin API changes detection behaviour on
 * the next evaluation.
 *
 * <p>{@code params} is a JSON object whose keys are rule-specific; see
 * {@link com.meridiantrust.sentinel.reference.model.RuleParams}. A typed
 * column per threshold would force a migration for every new rule, which is
 * exactly the coupling we are trying to avoid.
 */
@Entity
@Table(name = "rule_config")
@Getter
@Setter
@NoArgsConstructor
public class RuleConfig {

    @Id
    @Column(name = "rule_code", length = 48)
    private String ruleCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String typology;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Base contribution to the 0-100 risk score (business rule 7). */
    @Column(nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity = Severity.MEDIUM;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String params = "{}";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Integer version;
}
