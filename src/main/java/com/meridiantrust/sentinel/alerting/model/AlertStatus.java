package com.meridiantrust.sentinel.alerting.model;

import java.util.Set;

/**
 * Alert lifecycle.
 *
 * <p>Business rule 6 forbids silent deletion, so there is deliberately no
 * {@code DELETED} state — a cleared alert reaches {@link #CLOSED} carrying its
 * disposition, reason and the analyst's identity, and remains queryable
 * forever. Making the absence structural, rather than a convention, means no
 * future change can reintroduce deletion without redefining this enum.
 */
public enum AlertStatus {

    OPEN,
    IN_REVIEW,
    ESCALATED,
    CLOSED;

    /** Legal successor states — enforced centrally so no controller can bypass them. */
    public Set<AlertStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> Set.of(IN_REVIEW, ESCALATED, CLOSED);
            case IN_REVIEW -> Set.of(ESCALATED, CLOSED, OPEN);
            case ESCALATED -> Set.of(CLOSED, IN_REVIEW);
            // Terminal. Re-opening a disposed alert would break the audit
            // narrative; a new alert is raised instead.
            case CLOSED -> Set.of();
        };
    }

    public boolean canTransitionTo(AlertStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return this == CLOSED;
    }
}
