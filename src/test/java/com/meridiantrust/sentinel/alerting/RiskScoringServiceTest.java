package com.meridiantrust.sentinel.alerting;

import com.meridiantrust.sentinel.alerting.service.RiskScoringService;
import com.meridiantrust.sentinel.alerting.model.ScoreBreakdown;
import com.meridiantrust.sentinel.common.model.RiskRating;
import com.meridiantrust.sentinel.detection.model.RuleHit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.meridiantrust.sentinel.detection.RuleTestSupport.customer;
import static org.assertj.core.api.Assertions.assertThat;

/** Business rule 7: weighted 0-100 risk scoring and case-level aggregation. */
class RiskScoringServiceTest {

    private final RiskScoringService service = new RiskScoringService();

    private RuleHit hit(int jurisdictionUplift, String magnitudeRatio) {
        return RuleHit.builder()
                .ruleCode("TEST").typology("TEST")
                .customerId("CUST_00001").accountId("ACC_000001")
                .dedupKey("TEST|1").explanation("test")
                .evidenceTransactionIds(List.of("T1"))
                .evidenceAmountBase(BigDecimal.TEN)
                .jurisdictionUplift(jurisdictionUplift)
                .magnitudeRatio(new BigDecimal(magnitudeRatio))
                .build();
    }

    @Test
    @DisplayName("a low-risk customer with no uplifts scores the rule weight")
    void baseWeightOnly() {
        ScoreBreakdown breakdown = service.score(hit(0, "1.0"), 30,
                customer("CUST_00001", RiskRating.LOW, false), 0);

        assertThat(breakdown.total()).isEqualTo(30);
        assertThat(breakdown.ruleWeight()).isEqualTo(30);
    }

    @Test
    @DisplayName("high-risk rating and PEP status both raise the score")
    void customerContextRaisesScore() {
        ScoreBreakdown breakdown = service.score(hit(0, "1.0"), 30,
                customer("CUST_00001", RiskRating.HIGH, true), 0);

        assertThat(breakdown.customerRiskUplift()).isEqualTo(15);
        assertThat(breakdown.pepUplift()).isEqualTo(10);
        assertThat(breakdown.total()).isEqualTo(55);
    }

    @Test
    @DisplayName("the score is clamped to 100 however many uplifts apply")
    void scoreIsClamped() {
        ScoreBreakdown breakdown = service.score(hit(40, "1000"), 100,
                customer("CUST_00001", RiskRating.HIGH, true), 10);

        assertThat(breakdown.total()).isEqualTo(100);
    }

    @Test
    @DisplayName("magnitude uplift is log-scaled and capped, not linear")
    void magnitudeUpliftIsLogScaled() {
        int at2x = service.score(hit(0, "2"), 30,
                customer("C", RiskRating.LOW, false), 0).magnitudeUplift();
        int at1000x = service.score(hit(0, "1000"), 30,
                customer("C", RiskRating.LOW, false), 0).magnitudeUplift();

        assertThat(at2x).isEqualTo(5);
        // 1000x is 500x larger than 2x but contributes only 3x the uplift, and
        // is capped — one huge transaction must not pin every alert to 100.
        assertThat(at1000x).isEqualTo(15);
    }

    @Test
    @DisplayName("repeat detections of the same pattern raise the score, up to a cap")
    void recurrenceUpliftCaps() {
        assertThat(service.score(hit(0, "1.0"), 30,
                customer("C", RiskRating.LOW, false), 2).recurrenceUplift()).isEqualTo(6);
        assertThat(service.score(hit(0, "1.0"), 30,
                customer("C", RiskRating.LOW, false), 50).recurrenceUplift()).isEqualTo(12);
    }

    @Test
    @DisplayName("noisy-OR aggregation stays below 100 where summing would saturate")
    void aggregationDoesNotSaturate() {
        // Summing three 50s gives 150 -> clamped to 100, destroying queue order.
        int aggregate = service.aggregate(List.of(50, 50, 50));

        assertThat(aggregate).isEqualTo(88);
        assertThat(aggregate).isLessThan(100);
    }

    @Test
    @DisplayName("aggregation is monotonic — more evidence never lowers risk")
    void aggregationIsMonotonic() {
        int two = service.aggregate(List.of(60, 40));
        int three = service.aggregate(List.of(60, 40, 30));

        assertThat(three).isGreaterThanOrEqualTo(two);
    }

    @Test
    @DisplayName("a case with severe alerts still outranks one with moderate alerts")
    void aggregationPreservesOrdering() {
        int moderate = service.aggregate(List.of(45, 45, 45));
        int severe = service.aggregate(List.of(85, 80, 75));

        assertThat(severe).isGreaterThan(moderate);
    }

    @Test
    @DisplayName("no alerts means no risk")
    void emptyAggregate() {
        assertThat(service.aggregate(List.of())).isZero();
    }
}
