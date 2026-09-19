package com.meridiantrust.sentinel.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Type-safe binding of the {@code sentinel.*} configuration namespace.
 *
 * <p>Immutable records rather than mutable beans: configuration is read many
 * times and written never, and constructor binding fails fast at startup on a
 * malformed value instead of surfacing a null at detection time.
 *
 * <p>Note what is deliberately absent — detection thresholds. Those live in the
 * {@code rule_config} table so compliance can retune them without a redeploy.
 */
@ConfigurationProperties(prefix = "sentinel")
public record SentinelProperties(
        String baseCurrency,
        Detection detection,
        Seed seed,
        Security security) {

    public record Detection(int workerThreads, int bulkChunkSize, int queueCapacity) {}

    public record Seed(boolean enabled, int customers, int transactions) {}

    public record Security(
            String analystPassword,
            String seniorPassword,
            String adminPassword,
            List<String> corsAllowedOrigins) {}
}
