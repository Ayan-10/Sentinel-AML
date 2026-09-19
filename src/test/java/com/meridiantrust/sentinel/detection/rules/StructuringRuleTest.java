package com.meridiantrust.sentinel.detection.rules;

import com.meridiantrust.sentinel.detection.rule.impl.StructuringRule;

import com.meridiantrust.sentinel.detection.model.RuleContext;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.transaction.model.Direction;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rule 2: three or more transactions from one account inside a 24-hour
 * window, each between 9,000 and 9,999.
 *
 * <p>Three independent conditions must all hold — count, window and band — so
 * each is tested from both sides. A rule that fires on two transactions is
 * noisy; one that needs four misses the stated requirement entirely.
 */
class StructuringRuleTest {

    private static final Map<String, Object> PARAMS = Map.of(
            "minCount", 3, "windowHours", 24,
            "lowerBound", 9000, "upperBound", 9999.99);

    private StructuringRule rule() {
        return new StructuringRule(configWith(StructuringRule.CODE, PARAMS));
    }

    @Test
    @DisplayName("two in-band transactions do not trigger — the minimum is three")
    void twoTransactionsDoNotTrigger() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9200.00"),
                txn("T2", BASE_TIME.plusHours(2), Direction.CREDIT, "9400.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("three in-band transactions inside 24h trigger a structuring alert")
    void threeTransactionsTrigger() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9200.00"),
                txn("T2", BASE_TIME.plusHours(4), Direction.CREDIT, "9400.00"),
                txn("T3", BASE_TIME.plusHours(9), Direction.CREDIT, "9850.00")));

        List<RuleHit> hits = rule().evaluate(ctx);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).evidenceTransactionIds()).containsExactly("T1", "T2", "T3");
        assertThat(hits.get(0).typology()).isEqualTo("STRUCTURING");
    }

    @Test
    @DisplayName("three in-band transactions spanning more than 24h do not trigger")
    void outsideWindowDoesNotTrigger() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9200.00"),
                txn("T2", BASE_TIME.plusHours(12), Direction.CREDIT, "9400.00"),
                txn("T3", BASE_TIME.plusHours(25), Direction.CREDIT, "9850.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("the band is inclusive at both ends: 9,000.00 and 9,999.99 both qualify")
    void bandIsInclusive() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9000.00"),
                txn("T2", BASE_TIME.plusHours(1), Direction.CREDIT, "9500.00"),
                txn("T3", BASE_TIME.plusHours(2), Direction.CREDIT, "9999.99")));

        assertThat(rule().evaluate(ctx)).hasSize(1);
    }

    @Test
    @DisplayName("amounts outside the band are excluded — 8,999.99 is below, 10,000.00 is a CTR matter")
    void outOfBandAmountsExcluded() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "8999.99"),
                txn("T2", BASE_TIME.plusHours(1), Direction.CREDIT, "10000.00"),
                txn("T3", BASE_TIME.plusHours(2), Direction.CREDIT, "9500.00")));

        // Only one transaction is in band, so the cluster never reaches three.
        assertThat(rule().evaluate(ctx)).isEmpty();
    }

    @Test
    @DisplayName("five transactions produce one alert, not three overlapping ones")
    void clustersDoNotOverlap() {
        List<Transaction> txns = List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9100.00"),
                txn("T2", BASE_TIME.plusHours(2), Direction.CREDIT, "9200.00"),
                txn("T3", BASE_TIME.plusHours(4), Direction.CREDIT, "9300.00"),
                txn("T4", BASE_TIME.plusHours(6), Direction.CREDIT, "9400.00"),
                txn("T5", BASE_TIME.plusHours(8), Direction.CREDIT, "9500.00"));

        List<RuleHit> hits = rule().evaluate(accountWindowContext(txns));

        // Redundant-alert avoidance: one pattern, one finding, all five as evidence.
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).evidenceTransactionIds()).hasSize(5);
    }

    @Test
    @DisplayName("explanation states the count, the total and the band")
    void explanationIsSpecific() {
        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9200.00"),
                txn("T2", BASE_TIME.plusHours(1), Direction.CREDIT, "9400.00"),
                txn("T3", BASE_TIME.plusHours(2), Direction.CREDIT, "9300.00")));

        String explanation = rule().evaluate(ctx).get(0).explanation();

        assertThat(explanation)
                .contains("3 transactions")
                .contains("27,900.00")
                .contains(ACCOUNT)
                .contains("24 hours");
    }

    @Test
    @DisplayName("thresholds are retunable: minCount 2 catches pairs the default misses")
    void thresholdsAreRetunable() {
        StructuringRule loosened = new StructuringRule(configWith(StructuringRule.CODE,
                Map.of("minCount", 2, "windowHours", 24,
                       "lowerBound", 9000, "upperBound", 9999.99)));

        RuleContext ctx = accountWindowContext(List.of(
                txn("T1", BASE_TIME, Direction.CREDIT, "9200.00"),
                txn("T2", BASE_TIME.plusHours(3), Direction.CREDIT, "9400.00")));

        assertThat(rule().evaluate(ctx)).isEmpty();
        assertThat(loosened.evaluate(ctx)).hasSize(1);
    }
}
