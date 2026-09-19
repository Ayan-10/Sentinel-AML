package com.meridiantrust.sentinel.detection.rules;

import com.meridiantrust.sentinel.detection.rule.impl.CtrThresholdRule;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.transaction.model.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rule 1: any single transaction at or above the reporting threshold.
 *
 * <p>The boundary is the rule. "At or above 10,000" means 10,000.00 fires and
 * 9,999.99 does not, and an off-by-one here is the difference between meeting a
 * regulatory obligation and missing it — so the boundary is tested explicitly
 * from both sides rather than left to a convenient round number.
 */
class CtrThresholdRuleTest {

    private static final Map<String, Object> PARAMS = Map.of("thresholdBase", 10_000);

    private CtrThresholdRule rule() {
        return new CtrThresholdRule(configWith(CtrThresholdRule.CODE, PARAMS));
    }

    @Nested
    @DisplayName("threshold boundary")
    class Boundary {

        @Test
        @DisplayName("9,999.99 does not breach the threshold")
        void justBelowThreshold() {
            RuleContext ctx = RuleContext.of(List.of(
                    txn("T1", BASE_TIME, Direction.CREDIT, "9999.99")));

            assertThat(rule().evaluate(ctx)).isEmpty();
        }

        @Test
        @DisplayName("exactly 10,000.00 breaches the threshold — 'at or above'")
        void exactlyAtThreshold() {
            RuleContext ctx = RuleContext.of(List.of(
                    txn("T1", BASE_TIME, Direction.CREDIT, "10000.00")));

            List<RuleHit> hits = rule().evaluate(ctx);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).ruleCode()).isEqualTo(CtrThresholdRule.CODE);
            assertThat(hits.get(0).evidenceTransactionIds()).containsExactly("T1");
        }

        @Test
        @DisplayName("10,000.01 breaches the threshold")
        void justAboveThreshold() {
            RuleContext ctx = RuleContext.of(List.of(
                    txn("T1", BASE_TIME, Direction.DEBIT, "10000.01")));

            assertThat(rule().evaluate(ctx)).hasSize(1);
        }
    }

    @Test
    @DisplayName("flags both credits and debits — the threshold is direction-agnostic")
    void flagsBothDirections() {
        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "25000.00"),
                txn("T2", BASE_TIME.plusHours(1), Direction.DEBIT, "30000.00")));

        assertThat(rule().evaluate(ctx)).hasSize(2);
    }

    @Test
    @DisplayName("explanation names the amount and the threshold, not just the rule")
    void explanationIsSpecific() {
        RuleContext ctx = RuleContext.of(List.of(
                txn("TXN_9001", BASE_TIME, Direction.CREDIT, "45000.00")));

        String explanation = rule().evaluate(ctx).get(0).explanation();

        assertThat(explanation)
                .contains("TXN_9001")
                .contains("45,000.00")
                .contains("10,000.00");
    }

    @Test
    @DisplayName("threshold is configurable without code change")
    void thresholdIsConfigurable() {
        CtrThresholdRule tightened = new CtrThresholdRule(
                configWith(CtrThresholdRule.CODE, Map.of("thresholdBase", 5_000)));
        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "7500.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();          // default 10,000 — no hit
        assertThat(tightened.evaluate(ctx)).hasSize(1);      // retuned to 5,000 — hit
    }

    @Test
    @DisplayName("dedup key is per transaction, so the same transaction never alerts twice")
    void dedupKeyIsPerTransaction() {
        RuleContext ctx = RuleContext.of(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "20000.00"),
                txn("T2", BASE_TIME, Direction.CREDIT, "20000.00")));

        List<RuleHit> hits = rule().evaluate(ctx);

        assertThat(hits).extracting(RuleHit::dedupKey).doesNotHaveDuplicates();
        assertThat(hits.get(0).dedupKey()).contains("T1");
    }
}
