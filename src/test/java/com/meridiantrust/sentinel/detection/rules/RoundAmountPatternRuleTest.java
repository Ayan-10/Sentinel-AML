package com.meridiantrust.sentinel.detection.rules;

import com.meridiantrust.sentinel.detection.rule.impl.RoundAmountPatternRule;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.transaction.model.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Repeated round-number amounts — a corroborating, deliberately low-weight signal. */
class RoundAmountPatternRuleTest {

    private static final Map<String, Object> PARAMS = Map.of(
            "minCount", 3, "windowHours", 24,
            "roundingUnit", 10_000, "minAmountBase", 50_000);

    private RoundAmountPatternRule rule() {
        return new RoundAmountPatternRule(configWith(RoundAmountPatternRule.CODE, PARAMS));
    }

    @Test
    @DisplayName("three exact multiples above the floor trigger")
    void threeRoundAmountsTrigger() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "50000.00"),
                txn("T2", BASE_TIME.plusHours(2), Direction.DEBIT, "100000.00"),
                txn("T3", BASE_TIME.plusHours(4), Direction.DEBIT, "150000.00")));

        assertThat(rule().evaluate(ctx)).hasSize(1);
    }

    @Test
    @DisplayName("non-round amounts are excluded, so the cluster never reaches the minimum")
    void nonRoundAmountsExcluded() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "50000.00"),
                txn("T2", BASE_TIME.plusHours(2), Direction.DEBIT, "97432.18"),
                txn("T3", BASE_TIME.plusHours(4), Direction.DEBIT, "61450.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("round amounts below the value floor are ignored")
    void belowFloorIgnored() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "10000.00"),
                txn("T2", BASE_TIME.plusHours(1), Direction.DEBIT, "20000.00"),
                txn("T3", BASE_TIME.plusHours(2), Direction.DEBIT, "30000.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }
}
