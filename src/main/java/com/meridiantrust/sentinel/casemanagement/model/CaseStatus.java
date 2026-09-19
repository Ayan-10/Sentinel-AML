package com.meridiantrust.sentinel.casemanagement.model;

import java.util.Set;

/**
 * Investigation lifecycle.
 *
 * <p>Transitions are declared on the enum rather than scattered across service
 * methods, so "which moves are legal" has exactly one answer in the codebase.
 */
public enum CaseStatus {

    NEW,
    INVESTIGATING,
    PENDING_APPROVAL,
    CLOSED;

    public Set<CaseStatus> allowedTransitions() {
        return switch (this) {
            case NEW -> Set.of(INVESTIGATING, CLOSED);
            case INVESTIGATING -> Set.of(PENDING_APPROVAL, CLOSED);
            case PENDING_APPROVAL -> Set.of(CLOSED, INVESTIGATING);
            case CLOSED -> Set.of();
        };
    }

    public boolean canTransitionTo(CaseStatus target) {
        return allowedTransitions().contains(target);
    }
}
