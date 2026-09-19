package com.meridiantrust.sentinel.alerting;

import com.meridiantrust.sentinel.alerting.model.Disposition;
import com.meridiantrust.sentinel.alerting.model.DispositionRecord;
import com.meridiantrust.sentinel.alerting.model.ProductivityMetrics;
import com.meridiantrust.sentinel.alerting.service.ProductivityCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calculation is a pure function over values, so it is tested without Spring,
 * a database or mocks — the same property the detection rules and the SAR
 * narrative composer rely on.
 */
class ProductivityCalculatorTest {

    private final ProductivityCalculator calculator = new ProductivityCalculator();
    private static final Instant T0 = Instant.parse("2026-09-01T09:00:00Z");

    private DispositionRecord record(String rule, Disposition disposition, long hoursToClose) {
        return new DispositionRecord(rule, disposition, T0, T0.plus(Duration.ofHours(hoursToClose)));
    }

    @Test
    @DisplayName("false-positive rate is null when nothing has been disposed, not zero")
    void unknownRateIsNullNotZero() {
        ProductivityMetrics m = calculator.calculate(500, 500, List.of(), List.of());

        // Reporting 0% over zero disposed cases would tell a compliance officer
        // the queue is perfectly precise — the opposite of the truth.
        assertThat(m.falsePositiveRatePct()).isNull();
        assertThat(m.avgHoursToDisposition()).isNull();
        assertThat(m.medianHoursToDisposition()).isNull();
        assertThat(m.disposedAlerts()).isZero();
    }

    @Test
    @DisplayName("false-positive rate is the share of disposed alerts closed as false positive")
    void falsePositiveRate() {
        ProductivityMetrics m = calculator.calculate(100, 60, List.of(
                record("CTR_THRESHOLD", Disposition.FALSE_POSITIVE, 4),
                record("CTR_THRESHOLD", Disposition.FALSE_POSITIVE, 6),
                record("CTR_THRESHOLD", Disposition.FALSE_POSITIVE, 8),
                record("STRUCTURING", Disposition.TRUE_POSITIVE, 10)), List.of());

        assertThat(m.falsePositiveRatePct()).isEqualTo(75.0);
        assertThat(m.disposedAlerts()).isEqualTo(4);
    }

    @Test
    @DisplayName("mean and median time to disposition are both reported")
    void meanAndMedian() {
        ProductivityMetrics m = calculator.calculate(10, 5, List.of(
                record("R", Disposition.FALSE_POSITIVE, 1),
                record("R", Disposition.FALSE_POSITIVE, 2),
                record("R", Disposition.TRUE_POSITIVE, 300)), List.of());

        // The mean is dragged up by one long investigation; the median is not.
        // Reporting only the mean would misrepresent a typical analyst's day.
        assertThat(m.avgHoursToDisposition()).isEqualTo(101.0);
        assertThat(m.medianHoursToDisposition()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("median of an even-sized set averages the middle pair")
    void medianOfEvenSet() {
        ProductivityMetrics m = calculator.calculate(10, 5, List.of(
                record("R", Disposition.FALSE_POSITIVE, 2),
                record("R", Disposition.FALSE_POSITIVE, 4),
                record("R", Disposition.TRUE_POSITIVE, 6),
                record("R", Disposition.TRUE_POSITIVE, 10)), List.of());

        assertThat(m.medianHoursToDisposition()).isEqualTo(5.0);
    }

    /** Builds {@code count} records for one rule, {@code fp} of them false positives. */
    private List<DispositionRecord> forRule(String rule, int count, int fp) {
        List<DispositionRecord> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(record(rule, i < fp ? Disposition.FALSE_POSITIVE : Disposition.TRUE_POSITIVE, 3));
        }
        return out;
    }

    @Test
    @DisplayName("per-rule quality ranks the noisiest rule first — it names what to retune")
    void perRuleQualityRanksNoisiestFirst() {
        List<DispositionRecord> records = new java.util.ArrayList<>();
        records.addAll(forRule("CTR_THRESHOLD", 40, 30));           // 75%
        records.addAll(forRule("HIGH_RISK_JURISDICTION", 25, 0));   // 0%

        List<ProductivityMetrics.RuleQuality> byRule =
                calculator.calculate(100, 50, records, List.of()).falsePositiveRateByRule();

        assertThat(byRule).hasSize(2);
        assertThat(byRule.get(0).ruleCode()).isEqualTo("CTR_THRESHOLD");
        assertThat(byRule.get(0).falsePositiveRatePct()).isEqualTo(75.0);
        assertThat(byRule.get(1).ruleCode()).isEqualTo("HIGH_RISK_JURISDICTION");
        assertThat(byRule.get(1).falsePositiveRatePct()).isZero();
    }

    @Test
    @DisplayName("a rule with too small a disposed sample reports no rate, and sorts last")
    void smallSampleReportsNoRate() {
        List<DispositionRecord> records = new java.util.ArrayList<>();
        records.addAll(forRule("CTR_THRESHOLD", 40, 20));            // 50%, 40 disposed
        records.addAll(forRule("HIGH_RISK_JURISDICTION", 3, 2));     // would read 67% on n=3

        List<ProductivityMetrics.RuleQuality> byRule =
                calculator.calculate(100, 50, records, List.of()).falsePositiveRateByRule();

        ProductivityMetrics.RuleQuality small = byRule.stream()
                .filter(r -> r.ruleCode().equals("HIGH_RISK_JURISDICTION")).findFirst().orElseThrow();

        // 2 of 3 is not "67% noisy" — publishing it would rank a precise rule
        // above a genuinely noisy one and send someone to retune the wrong control.
        assertThat(small.falsePositiveRatePct()).isNull();
        assertThat(small.sufficientSample()).isFalse();
        assertThat(small.disposed()).isEqualTo(3);

        assertThat(byRule.get(0).ruleCode()).isEqualTo("CTR_THRESHOLD");
        assertThat(byRule.get(byRule.size() - 1).ruleCode()).isEqualTo("HIGH_RISK_JURISDICTION");
    }

    @Test
    @DisplayName("SAR escalations are counted separately from other true positives")
    void countsSarEscalations() {
        ProductivityMetrics m = calculator.calculate(10, 5, List.of(
                record("R", Disposition.ESCALATED_TO_SAR, 8),
                record("R", Disposition.ESCALATED_TO_SAR, 9),
                record("R", Disposition.TRUE_POSITIVE, 4)), List.of());

        assertThat(m.escalatedToSar()).isEqualTo(2);
        assertThat(m.dispositionBreakdown())
                .containsEntry("ESCALATED_TO_SAR", 2L)
                .containsEntry("TRUE_POSITIVE", 1L)
                .containsEntry("FALSE_POSITIVE", 0L);
    }

    @Test
    @DisplayName("volume trend groups detections by day, oldest first")
    void volumeTrendGroupsByDay() {
        ProductivityMetrics m = calculator.calculate(4, 4, List.of(), List.of(
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-01T23:30:00Z"),
                Instant.parse("2026-09-03T08:00:00Z")));

        assertThat(m.alertVolumeTrend()).hasSize(2);
        assertThat(m.alertVolumeTrend().get(0).alertCount()).isEqualTo(2);
        assertThat(m.alertVolumeTrend().get(0).day().toString()).isEqualTo("2026-09-01");
        assertThat(m.alertVolumeTrend().get(1).alertCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a negative interval from clock skew is excluded rather than dragging the mean down")
    void ignoresNegativeIntervals() {
        ProductivityMetrics m = calculator.calculate(10, 5, List.of(
                new DispositionRecord("R", Disposition.FALSE_POSITIVE, T0, T0.minus(Duration.ofHours(5))),
                record("R", Disposition.TRUE_POSITIVE, 10)), List.of());

        assertThat(m.avgHoursToDisposition()).isEqualTo(10.0);
    }
}
