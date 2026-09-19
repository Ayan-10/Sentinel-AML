package com.meridiantrust.sentinel.sar.model;

/**
 * The slice of an alert the narrative composer needs.
 *
 * <p>Deliberately not the {@code Alert} entity. Passing a JPA entity into the
 * composer would couple narrative generation to the persistence model and drag
 * a Hibernate session into what should be a pure text transformation. With this
 * record the composer is a function from values to a string — unit-testable
 * with no Spring context, no database and no mocks, exactly like the detection
 * rules.
 */
public record AlertEvidence(
        String alertRef,
        String ruleCode,
        String typology,
        int riskScore,
        String severity,
        String explanation,
        int evidenceCount) {
}
