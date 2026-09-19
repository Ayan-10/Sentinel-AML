package com.meridiantrust.sentinel.alerting.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Analyst productivity metrics — how well the monitoring programme is running,
 * as distinct from what it has found.
 *
 * <p>These are the numbers a Chief Compliance Officer is asked for by a
 * regulator: are alerts being worked, how fast, and how much of the queue is
 * noise.
 */
@Schema(description = "Operational metrics for the monitoring programme.")
public record ProductivityMetrics(

        long totalAlerts,
        long disposedAlerts,
        long openAlerts,

        @Schema(description = "Disposed alerts closed as FALSE_POSITIVE, as a percentage of all "
                            + "disposed alerts. Null when nothing has been disposed yet — a rate "
                            + "over zero cases is not 0%, it is unknown.")
        Double falsePositiveRatePct,

        @Schema(description = "Mean hours from detection to disposition")
        Double avgHoursToDisposition,

        @Schema(description = "Median hours to disposition. Reported alongside the mean because a "
                            + "handful of long-running investigations skew an average badly.")
        Double medianHoursToDisposition,

        @Schema(description = "Alerts escalated for SAR filing")
        long escalatedToSar,

        @Schema(description = "Count of disposed alerts by outcome")
        Map<String, Long> dispositionBreakdown,

        @Schema(description = "False-positive rate per rule — identifies which rule is generating "
                            + "noise and should be retuned. Rules with too small a disposed sample "
                            + "report a null rate rather than a misleading one.")
        List<RuleQuality> falsePositiveRateByRule,

        @Schema(description = "Alerts detected per day, oldest first")
        List<DailyAlertVolume> alertVolumeTrend) {

    @Schema(description = "Per-rule alert quality.")
    public record RuleQuality(
            String ruleCode,
            long disposed,
            long falsePositives,
            @Schema(description = "Null when too few alerts have been disposed for the rate to "
                                + "mean anything. A rate over three cases is noise, and publishing "
                                + "it would rank a precise rule as though it were a noisy one.")
            Double falsePositiveRatePct,
            @Schema(description = "False when the sample is below the reporting threshold")
            boolean sufficientSample) {}

    @Schema(description = "Alerts detected on one day.")
    public record DailyAlertVolume(LocalDate day, long alertCount) {}
}
