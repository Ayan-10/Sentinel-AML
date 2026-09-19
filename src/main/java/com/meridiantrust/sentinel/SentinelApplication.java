package com.meridiantrust.sentinel;


import com.meridiantrust.sentinel.common.config.SentinelProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Sentinel — Transaction Monitoring System for MeridianTrust Bank.
 *
 * <p>A modular monolith with hexagonal boundaries at its edges. See
 * {@code docs/ARCHITECTURE.md} for the design rationale.
 */
@SpringBootApplication
@EnableConfigurationProperties(SentinelProperties.class)
@EnableTransactionManagement
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
    }
}
