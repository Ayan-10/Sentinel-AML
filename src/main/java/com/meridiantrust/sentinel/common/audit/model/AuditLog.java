package com.meridiantrust.sentinel.common.audit.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An immutable audit record.
 *
 * <p>NFR (Auditability): all alert and case state transitions are logged
 * immutably with timestamp and actor identity. Immutability is enforced
 * structurally — {@link AuditLogRepository} exposes no update or delete
 * operation, and the entity's fields are set once at construction.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 32, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 40, updatable = false)
    private String entityId;

    @Column(nullable = false, length = 48, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(name = "actor_roles", updatable = false)
    private String actorRoles;

    @Column(name = "from_state", length = 32, updatable = false)
    private String fromState;

    @Column(name = "to_state", length = 32, updatable = false)
    private String toState;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    /**
     * Constructed only by {@code AuditService}. Public because that service now
     * lives in a sibling package; immutability does not depend on this modifier
     * — it is enforced by {@code updatable = false} on every column and by
     * {@code AuditLogRepository} exposing no update or delete operation.
     */
    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }

    public AuditLog(String entityType, String entityId, String action, String actor,
             String actorRoles, String fromState, String toState, String details) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.actorRoles = actorRoles;
        // Defensive truncation. These columns are sized for state names; an
        // oversized value is a caller bug, but it must not turn into a failed
        // business operation, so it is clipped rather than allowed to blow up
        // the insert.
        this.fromState = clip(fromState, 32);
        this.toState = clip(toState, 32);
        this.details = details;
        this.occurredAt = Instant.now();
    }
}
