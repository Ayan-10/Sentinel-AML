package com.meridiantrust.sentinel.detection.rules;

import com.meridiantrust.sentinel.detection.rule.impl.RapidMovementRule;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.transaction.model.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rule 3: 80% or more of a deposit leaving within 48 hours.
 *
 * <p>The guard tests matter as much as the trigger tests here. Without the
 * minimum-deposit floor this rule fires on ordinary salary-and-spend behaviour,
 * which would bury the analyst queue in false positives and make the whole
 * system less useful than the spreadsheet it replaces.
 */
class RapidMovementRuleTest {

    private static final Map<String, Object> PARAMS = Map.of(
            "windowHours", 48, "outflowRatio", 0.80, "minDepositBase", 50_000);

    private RapidMovementRule rule() {
        return new RapidMovementRule(configWith(RapidMovementRule.CODE, PARAMS));
    }

    @Test
    @DisplayName("79% outflow does not trigger — just below the ratio")
    void justBelowRatio() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("DEP", BASE_TIME, Direction.CREDIT, "100000.00"),
                txn("OUT", BASE_TIME.plusHours(5), Direction.DEBIT, "79000.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("exactly 80% outflow triggers — the threshold is inclusive")
    void exactlyAtRatio() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("DEP", BASE_TIME, Direction.CREDIT, "100000.00"),
                txn("OUT", BASE_TIME.plusHours(5), Direction.DEBIT, "80000.00")));

        List<RuleHit> hits = rule().evaluate(ctx);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).typology()).isEqualTo("LAYERING");
        assertThat(hits.get(0).evidenceTransactionIds()).containsExactly("DEP", "OUT");
    }

    @Test
    @DisplayName("outflow after the 48-hour window does not trigger")
    void outsideWindow() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("DEP", BASE_TIME, Direction.CREDIT, "100000.00"),
                txn("OUT", BASE_TIME.plusHours(49), Direction.DEBIT, "95000.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("a deposit below the floor does not trigger — ordinary salary-and-spend is not layering")
    void smallDepositIgnored() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("DEP", BASE_TIME, Direction.CREDIT, "20000.00"),
                txn("OUT", BASE_TIME.plusHours(3), Direction.DEBIT, "19500.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("dispersal across several counterparties aggregates into one finding")
    void multipleOutflowsAggregate() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("DEP", BASE_TIME, Direction.CREDIT, "500000.00"),
                txn("O1", BASE_TIME.plusHours(4), Direction.DEBIT, "150000.00"),
                txn("O2", BASE_TIME.plusHours(12), Direction.DEBIT, "160000.00"),
                txn("O3", BASE_TIME.plusHours(30), Direction.DEBIT, "140000.00")));

        List<RuleHit> hits = rule().evaluate(ctx);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).evidenceTransactionIds()).containsExactly("DEP", "O1", "O2", "O3");
        assertThat(hits.get(0).explanation()).contains("3 outbound transaction(s)");
    }

    @Test
    @DisplayName("a deposit with no outflow does not trigger")
    void depositWithoutOutflow() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("DEP", BASE_TIME, Direction.CREDIT, "500000.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("outflow preceding the deposit is not counted — sequence matters")
    void outflowBeforeDepositIgnored() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("OUT", BASE_TIME, Direction.DEBIT, "95000.00"),
                txn("DEP", BASE_TIME.plusHours(5), Direction.CREDIT, "100000.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }
}
