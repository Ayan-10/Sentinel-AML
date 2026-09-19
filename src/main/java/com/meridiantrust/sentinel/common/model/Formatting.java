package com.meridiantrust.sentinel.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formatting helpers for the human-readable alert explanations.
 *
 * <p>The brief requires alerts to explain <em>why</em> they fired. An
 * explanation an analyst has to decode is not an explanation, so amounts are
 * rendered grouped and currency-prefixed rather than as raw {@code BigDecimal}
 * {@code toString()} output.
 */
public final class Formatting {

    private Formatting() {}

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** {@code 1234567.5} → {@code INR 12,34,567.50} (Indian grouping). */
    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "INR 0.00";
        }
        DecimalFormat df = new DecimalFormat("##,##,##,##,##0.00");
        return "INR " + df.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public static String percent(BigDecimal ratio) {
        if (ratio == null) {
            return "0%";
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%";
    }

    public static String multiplier(BigDecimal value) {
        if (value == null) {
            return "0x";
        }
        return value.setScale(1, RoundingMode.HALF_UP) + "x";
    }

    public static String timestamp(LocalDateTime ts) {
        return ts == null ? "unknown" : ts.format(TIMESTAMP);
    }

    public static String date(LocalDateTime ts) {
        return ts == null ? "unknown" : ts.format(DATE);
    }
}
