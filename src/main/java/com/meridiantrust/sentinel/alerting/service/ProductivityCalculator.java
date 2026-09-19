package com.meridiantrust.sentinel.alerting.service;

import com.meridiantrust.sentinel.alerting.model.Disposition;
import com.meridiantrust.sentinel.alerting.model.DispositionRecord;
import com.meridiantrust.sentinel.alerting.model.ProductivityMetrics;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes productivity metrics from disposition records.
 *
 * <p>A pure function: no I/O, no entities, no Spring needed to exercise it. The
 * arithmetic here is where the subtle mistakes live, so isolating it makes those
 * mistakes testable.
 */
@Component
public class ProductivityCalculator {

    /**
     * Minimum disposed alerts before a per-rule false-positive rate is reported.
     *
     * <p>Without this, a rule with three disposed alerts and two false positives
     * publishes a "67% false-positive rate" and outranks a genuinely noisy rule
     * with a thousand. A compliance officer would retune the wrong control. Below
     * the threshold the rate is reported as unknown, not as a number.
     */
    private static final int MIN_SAMPLE_FOR_RATE = 20;

    public ProductivityMetrics calculate(long totalAlerts,
                                         long openAlerts,
                                         List<DispositionRecord> disposed,
                                         List<Instant> detectionTimes) {

        long disposedCount = disposed.size();

        // A rate over zero cases is *unknown*, not zero. Reporting 0% when
        // nothing has been worked would tell a compliance officer the queue is
        // perfectly precise, which is the opposite of the truth.
        Double falsePositiveRate = disposedCount == 0
                ? null
                : percentage(countOf(disposed, Disposition.FALSE_POSITIVE), disposedCount);

        List<Double> hours = disposed.stream()
                .filter(r -> r.firstDetectedAt() != null && r.disposedAt() != null)
                .map(r -> Duration.between(r.firstDetectedAt(), r.disposedAt()).toMinutes() / 60.0)
                // A clock skew or a backdated record must not produce a negative
                // "time to disposition" that drags the mean below zero.
                .filter(h -> h >= 0)
                .sorted()
                .toList();

        return new ProductivityMetrics(
                totalAlerts,
                disposedCount,
                openAlerts,
                falsePositiveRate,
                mean(hours),
                median(hours),
                countOf(disposed, Disposition.ESCALATED_TO_SAR),
                breakdown(disposed),
                perRuleQuality(disposed),
                volumeTrend(detectionTimes));
    }

    private long countOf(List<DispositionRecord> records, Disposition disposition) {
        return records.stream().filter(r -> r.disposition() == disposition).count();
    }

    private Map<String, Long> breakdown(List<DispositionRecord> records) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Disposition d : Disposition.values()) {
            counts.put(d.name(), countOf(records, d));
        }
        return counts;
    }

    /**
     * Per-rule false-positive rate — the metric that actually drives action,
     * because it names which rule to retune rather than just reporting that the
     * queue is noisy.
     */
    private List<ProductivityMetrics.RuleQuality> perRuleQuality(List<DispositionRecord> records) {
        Map<String, List<DispositionRecord>> byRule = records.stream()
                .collect(Collectors.groupingBy(DispositionRecord::ruleCode));

        return byRule.entrySet().stream()
                .map(e -> {
                    long total = e.getValue().size();
                    long fp = e.getValue().stream()
                            .filter(r -> r.disposition() == Disposition.FALSE_POSITIVE).count();
                    boolean sufficient = total >= MIN_SAMPLE_FOR_RATE;
                    return new ProductivityMetrics.RuleQuality(
                            e.getKey(), total, fp,
                            sufficient ? percentage(fp, total) : null,
                            sufficient);
                })
                // Noisiest first — that is the rule to retune. Rules whose sample
                // is too small sort last rather than being interleaved as if
                // their rate were comparable.
                .sorted(Comparator
                        .comparing((ProductivityMetrics.RuleQuality q) -> q.falsePositiveRatePct() == null)
                        .thenComparing(q -> q.falsePositiveRatePct() == null ? 0d : q.falsePositiveRatePct(),
                                      Comparator.reverseOrder())
                        .thenComparing(ProductivityMetrics.RuleQuality::disposed, Comparator.reverseOrder()))
                .toList();
    }

    private List<ProductivityMetrics.DailyAlertVolume> volumeTrend(List<Instant> detectionTimes) {
        Map<LocalDate, Long> perDay = detectionTimes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        t -> t.atZone(ZoneOffset.UTC).toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()));

        return perDay.entrySet().stream()
                .map(e -> new ProductivityMetrics.DailyAlertVolume(e.getKey(), e.getValue()))
                .toList();
    }

    private double percentage(long part, long whole) {
        if (whole == 0) {
            return 0d;
        }
        return Math.round((part * 10000.0) / whole) / 100.0;
    }

    private Double mean(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        return round(sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    /** Expects a pre-sorted list. */
    private Double median(List<Double> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int size = sorted.size();
        double value = size % 2 == 1
                ? sorted.get(size / 2)
                : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        return round(value);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
