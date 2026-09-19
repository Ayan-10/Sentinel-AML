package com.meridiantrust.sentinel.reference.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Typed, defaulted read access over a rule's JSON parameter blob.
 *
 * <p>Rules must never index into a raw map. Two reasons: a missing key would
 * throw at detection time (silently disabling a control), and a JSON number
 * arrives as {@code Integer}, {@code Double} or {@code BigDecimal} depending on
 * how it was written. Every accessor here takes a default and coerces the type,
 * so a malformed or partial config degrades to documented behaviour instead of
 * an exception mid-batch.
 */
public record RuleParams(Map<String, Object> values) {

    public static RuleParams empty() {
        return new RuleParams(Map.of());
    }

    public int getInt(String key, int defaultValue) {
        Object raw = values.get(key);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        Object raw = values.get(key);
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (raw instanceof String s) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object raw = values.get(key);
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return defaultValue;
    }

    public String getString(String key, String defaultValue) {
        Object raw = values.get(key);
        return raw == null ? defaultValue : String.valueOf(raw);
    }
}
