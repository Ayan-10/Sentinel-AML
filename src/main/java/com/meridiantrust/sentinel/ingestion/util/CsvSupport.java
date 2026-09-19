package com.meridiantrust.sentinel.ingestion.util;

import com.meridiantrust.sentinel.common.error.ApiException;
import com.opencsv.CSVReaderHeaderAware;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Header-aware CSV reading.
 *
 * <p>Reads by column <em>name</em> rather than position, so a supplier adding a
 * column — or ordering them differently from the sample files — does not
 * silently shift every field by one. Positional parsing fails in the worst
 * possible way here: it does not error, it produces plausible wrong data.
 */
public final class CsvSupport {

    private CsvSupport() {}

    /** Reads the whole file into header-keyed rows. */
    public static List<Map<String, String>> readAll(InputStream inputStream) {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVReaderHeaderAware csvReader = new CSVReaderHeaderAware(reader)) {

            Map<String, String> row;
            while ((row = csvReader.readMap()) != null) {
                Map<String, String> normalised = new HashMap<>();
                row.forEach((key, value) -> normalised.put(
                        key == null ? "" : key.trim().toLowerCase(),
                        value == null ? null : value.trim()));
                rows.add(normalised);
            }
        } catch (IOException | com.opencsv.exceptions.CsvValidationException ex) {
            throw new ApiException.Validation("Could not read CSV: " + ex.getMessage());
        }
        return rows;
    }

    public static String get(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    public static String get(Map<String, String> row, String key, String fallback) {
        String value = get(row, key);
        return value != null ? value : fallback;
    }

    /** CSV booleans arrive as Y/N, true/false, 1/0 depending on the source system. */
    public static boolean bool(Map<String, String> row, String key) {
        String value = get(row, key);
        if (value == null) {
            return false;
        }
        return switch (value.trim().toUpperCase()) {
            case "Y", "YES", "TRUE", "1" -> true;
            default -> false;
        };
    }

    public static Integer integer(Map<String, String> row, String key) {
        String value = get(row, key);
        if (value == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static java.math.BigDecimal decimal(Map<String, String> row, String key) {
        String value = get(row, key);
        if (value == null) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
