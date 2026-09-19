package com.meridiantrust.sentinel.ingestion.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lenient timestamp parsing across the formats core-banking exports actually
 * emit.
 *
 * <p>Being permissive here is a deliberate trade. Data arrives from systems we
 * do not control, and rejecting a whole file because one upstream team writes
 * {@code dd/MM/yyyy} would make the platform brittle for no compliance benefit.
 * Returning {@code null} rather than throwing lets the validation chain report
 * an unparseable value as a normal row rejection.
 */
public final class TimestampParser {

    private TimestampParser() {}

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));

    private static final List<DateTimeFormatter> DATE_ONLY_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    /** @return the parsed value, or {@code null} when no known format matches */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace('T', ' ').replace("Z", "");
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(raw.trim(), format);
            } catch (Exception ignored) {
                // try the next format
            }
            try {
                return LocalDateTime.parse(value, format);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        for (DateTimeFormatter format : DATE_ONLY_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), format).atStartOfDay();
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return null;
    }

    public static LocalDate parseDate(String raw) {
        LocalDateTime parsed = parse(raw);
        return parsed == null ? null : parsed.toLocalDate();
    }
}
