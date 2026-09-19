package com.meridiantrust.sentinel.detection.rule;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.detection.model.RuleScope;

import java.util.List;

/**
 * The Strategy contract every AML typology implements.
 *
 * <p>This interface is the extension point that keeps the engine closed for
 * modification and open for extension. Adding a seventh typology — trade-based
 * laundering, mule-account networks, whatever the next regulatory cycle brings
 * — means writing one {@code @Component} implementing this interface and
 * inserting one {@code rule_config} row. {@link DetectionEngine} is not edited,
 * recompiled against new types, or even aware that the rule exists: Spring
 * injects it into the engine's {@code List<DetectionRule>} automatically.
 *
 * <p><b>Implementations must be stateless and side-effect free.</b> No I/O, no
 * persistence, no mutable fields. Everything needed arrives in
 * {@link RuleContext}; everything produced is returned as {@link RuleHit}.
 * This is what allows rules to run in parallel without synchronisation and to
 * be unit-tested without infrastructure.
 */
public interface DetectionRule {

    /** Stable identifier; must match a {@code rule_config.rule_code} row. */
    String ruleCode();

    /** The laundering typology this rule detects, surfaced on the alert. */
    String typology();

    /** Declares which pre-loaded data this rule reads. */
    RuleScope scope();

    /**
     * Evaluates the context and reports findings.
     *
     * @return zero or more hits; never {@code null}
     */
    List<RuleHit> evaluate(RuleContext context);
}
