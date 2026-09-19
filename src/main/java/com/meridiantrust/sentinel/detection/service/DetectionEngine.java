package com.meridiantrust.sentinel.detection.service;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.detection.model.RuleScope;
import com.meridiantrust.sentinel.detection.rule.DetectionRule;

import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates rule evaluation. The engine knows the {@link DetectionRule}
 * abstraction and nothing about any concrete typology.
 *
 * <p>Spring injects every {@code DetectionRule} bean on the classpath into the
 * constructor, so the set of active typologies is determined by what exists,
 * not by a list maintained here. Adding a rule requires no change to this file
 * — the Open/Closed Principle made operational rather than aspirational.
 */
@Component
public class DetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngine.class);

    private final List<DetectionRule> rules;
    private final RuleConfigProvider configProvider;

    public DetectionEngine(List<DetectionRule> rules, RuleConfigProvider configProvider) {
        this.rules = List.copyOf(rules);
        this.configProvider = configProvider;
        log.info("Detection engine initialised with {} rule(s): {}",
                rules.size(), rules.stream().map(DetectionRule::ruleCode).toList());
    }

    /**
     * Evaluates every enabled rule against the context.
     *
     * @return all hits found; never {@code null}
     */
    public List<RuleHit> evaluate(RuleContext context) {
        if (context.transactions().isEmpty()) {
            return List.of();
        }
        List<RuleHit> hits = new ArrayList<>();
        for (DetectionRule rule : rules) {
            if (!configProvider.isEnabled(rule.ruleCode())) {
                continue;
            }
            hits.addAll(safeEvaluate(rule, context));
        }
        return hits;
    }

    /**
     * Fault isolation at the rule boundary.
     *
     * <p>A defect in one typology must never prevent the others from running.
     * In an AML system the asymmetry is stark: a crash in the round-number rule
     * that aborts the batch would also stop the structuring rule from catching
     * an actual launderer. So a failing rule is logged loudly and contributes
     * nothing, while the rest of the engine carries on.
     */
    private List<RuleHit> safeEvaluate(DetectionRule rule, RuleContext context) {
        try {
            List<RuleHit> hits = rule.evaluate(context);
            return hits == null ? List.of() : hits;
        } catch (RuntimeException ex) {
            log.error("Rule {} failed on a chunk of {} transaction(s) — skipping this rule for the chunk. "
                            + "Other rules are unaffected.",
                    rule.ruleCode(), context.transactions().size(), ex);
            return List.of();
        }
    }

    public List<String> activeRuleCodes() {
        return rules.stream()
                .map(DetectionRule::ruleCode)
                .filter(configProvider::isEnabled)
                .toList();
    }

    /** Longest lookback any account-scoped rule needs, in hours. Drives window loading. */
    public int maxLookbackHours() {
        return rules.stream()
                .filter(r -> r.scope() == RuleScope.ACCOUNT_WINDOW)
                .map(r -> configProvider.paramsFor(r.ruleCode()).getInt("windowHours", 24))
                .max(Integer::compareTo)
                .orElse(48);
    }
}
