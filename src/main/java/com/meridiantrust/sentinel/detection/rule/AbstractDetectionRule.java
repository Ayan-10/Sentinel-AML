package com.meridiantrust.sentinel.detection.rule;

import com.meridiantrust.sentinel.detection.model.RuleHit;

import com.meridiantrust.sentinel.reference.service.RuleConfigProvider;
import com.meridiantrust.sentinel.reference.model.RuleParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Template Method base for detection rules.
 *
 * <p>Every rule needs the same scaffolding: read its configuration, build a
 * canonical deduplication key, assemble an explanation, log the hit. Repeating
 * that in six classes would mean six chances to build a dedup key slightly
 * differently — and an inconsistent dedup key silently breaks the
 * no-duplicate-alerts guarantee rather than failing loudly.
 *
 * <p>So the scaffolding lives here once, and a concrete rule contains only its
 * typology logic. That is the Single Responsibility Principle paying for itself
 * in defect prevention, not just tidiness: each new rule is roughly 40 lines of
 * genuine detection logic.
 */
public abstract class AbstractDetectionRule implements DetectionRule {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final RuleConfigProvider configProvider;

    protected AbstractDetectionRule(RuleConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /** This rule's runtime-tunable parameters, never {@code null}. */
    protected RuleParams params() {
        return configProvider.paramsFor(ruleCode());
    }

    protected int weight() {
        return configProvider.weightOf(ruleCode());
    }

    // --- Deduplication key construction -----------------------------------
    // Business requirement: "one customer doesn't generate 50 redundant alerts
    // for the same underlying pattern." The key identifies the PATTERN, not the
    // triggering row, so repeated detections of one pattern fold into a single
    // alert with a rising trigger count.

    /** Per-transaction pattern — inherently unique (CTR, jurisdiction). */
    protected String dedupKeyForTransaction(String transactionId) {
        return "%s|TXN|%s".formatted(ruleCode(), transactionId);
    }

    /**
     * Per-account pattern bucketed by day. An account structuring across a week
     * yields one alert per day with an incrementing trigger count — not one per
     * transaction, which is precisely the alert-flood the brief warns against.
     */
    protected String dedupKeyForAccountDay(String accountId, LocalDateTime windowStart) {
        LocalDate day = windowStart == null ? LocalDate.now() : windowStart.toLocalDate();
        return "%s|ACCT|%s|%s".formatted(ruleCode(), accountId, day);
    }

    /** Per-customer pattern bucketed by day (behavioural baseline breaches). */
    protected String dedupKeyForCustomerDay(String customerId, LocalDate day) {
        return "%s|CUST|%s|%s".formatted(ruleCode(), customerId, day);
    }

    /** Anchored to a specific originating transaction (rapid movement → the deposit). */
    protected String dedupKeyForAnchor(String accountId, String anchorTransactionId) {
        return "%s|ANCH|%s|%s".formatted(ruleCode(), accountId, anchorTransactionId);
    }

    // --- Hit assembly ------------------------------------------------------

    protected RuleHit.RuleHitBuilder hit() {
        return RuleHit.builder()
                .ruleCode(ruleCode())
                .typology(typology())
                .jurisdictionUplift(0)
                .magnitudeRatio(BigDecimal.ONE);
    }

    protected void traceHit(String dedupKey, String explanation) {
        log.debug("Rule {} hit [{}]: {}", ruleCode(), dedupKey, explanation);
    }

    /** Null-safe empty result, for readability at early-return sites. */
    protected List<RuleHit> noHits() {
        return List.of();
    }
}
