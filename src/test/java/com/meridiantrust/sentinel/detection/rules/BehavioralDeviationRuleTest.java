package com.meridiantrust.sentinel.detection.rules;

import com.meridiantrust.sentinel.detection.rule.impl.BehavioralDeviationRule;

import com.meridiantrust.sentinel.detection.model.RuleHit;
import com.meridiantrust.sentinel.transaction.model.DailyVolume;
import com.meridiantrust.sentinel.transaction.model.Direction;
import com.meridiantrust.sentinel.transaction.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business rule 5: a daily value exceeding 3x the customer's 90-day rolling
 * average.
 *
 * <p>The cold-start test is the one that earns its keep. Without a minimum
 * baseline, every newly-onboarded customer's third transaction looks like a
 * 3x deviation, and this rule alone would out-produce the other five combined
 * with pure noise.
 */
class BehavioralDeviationRuleTest {

    private static final Map<String, Object> PARAMS = Map.of(
            "multiplier", 3.0, "baselineDays", 90,
            "minBaselineTxns", 5, "minDailyBase", 25_000);

    private BehavioralDeviationRule rule() {
        return new BehavioralDeviationRule(configWith(BehavioralDeviationRule.CODE, PARAMS));
    }

    /** 90 days of history at {@code perDay}, giving a rolling average of exactly that. */
    private List<DailyVolume> steadyHistory(String perDay, int days) {
        List<DailyVolume> history = new ArrayList<>();
        for (int i = days; i >= 1; i--) {
            history.add(volume(BASE_TIME.minusDays(i), perDay, 2));
        }
        return history;
    }

    @Test
    @DisplayName("a day at 2.9x the average does not trigger")
    void justBelowMultiplier() {
        // 90 days at 1,000/day => 90,000 total over 90 days => average 1,000/day.
        List<DailyVolume> history = steadyHistory("1000.00", 90);
        history.add(volume(BASE_TIME, "2900.00", 1));

        List<Transaction> chunk = List.of(txn("T1", BASE_TIME, Direction.DEBIT, "2900.00"));

        // Below minDailyBase as well, so this is doubly excluded.
        assertThat(rule().evaluate(customerWindowContext(chunk, history))).isEmpty();
    }

    @Test
    @DisplayName("a day above 3x the average, and above the value floor, triggers")
    void aboveMultiplierTriggers() {
        // 90 days at 10,000/day => average 10,000/day. A 620,000 day is 62x.
        List<DailyVolume> history = steadyHistory("10000.00", 90);
        history.add(volume(BASE_TIME, "620000.00", 3));

        List<Transaction> chunk = List.of(
                txn("T1", BASE_TIME, Direction.DEBIT, "200000.00"),
                txn("T2", BASE_TIME.plusHours(2), Direction.DEBIT, "210000.00"),
                txn("T3", BASE_TIME.plusHours(4), Direction.DEBIT, "210000.00"));

        List<RuleHit> hits = rule().evaluate(customerWindowContext(chunk, history));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).typology()).isEqualTo("BEHAVIOURAL_ANOMALY");
        assertThat(hits.get(0).evidenceTransactionIds()).containsExactly("T1", "T2", "T3");
    }

    @Test
    @DisplayName("cold start: too few baseline transactions means no alert")
    void coldStartSuppressed() {
        // Only two prior days of history — no defensible average exists yet.
        List<DailyVolume> history = new ArrayList<>(List.of(
                volume(BASE_TIME.minusDays(2), "1000.00", 1),
                volume(BASE_TIME.minusDays(1), "1000.00", 1)));
        history.add(volume(BASE_TIME, "500000.00", 1));

        List<Transaction> chunk = List.of(txn("T1", BASE_TIME, Direction.DEBIT, "500000.00"));

        assertThat(rule().evaluate(customerWindowContext(chunk, history))).isEmpty();
    }

    @Test
    @DisplayName("a day below the minimum value floor does not trigger, however large the multiple")
    void belowValueFloorSuppressed() {
        // Average 10/day; a 3,000 day is 300x — but 3,000 is not worth an analyst's time.
        List<DailyVolume> history = steadyHistory("10.00", 90);
        history.add(volume(BASE_TIME, "3000.00", 1));

        List<Transaction> chunk = List.of(txn("T1", BASE_TIME, Direction.DEBIT, "3000.00"));

        assertThat(rule().evaluate(customerWindowContext(chunk, history))).isEmpty();
    }

    @Test
    @DisplayName("explanation states the observed multiple and the baseline")
    void explanationIsSpecific() {
        List<DailyVolume> history = steadyHistory("10000.00", 90);
        history.add(volume(BASE_TIME, "620000.00", 3));
        List<Transaction> chunk = List.of(txn("T1", BASE_TIME, Direction.DEBIT, "620000.00"));

        String explanation = rule().evaluate(customerWindowContext(chunk, history))
                .get(0).explanation();

        assertThat(explanation)
                .contains(CUSTOMER)
                .contains("620,000.00")
                .contains("90-day rolling average");
    }
}
