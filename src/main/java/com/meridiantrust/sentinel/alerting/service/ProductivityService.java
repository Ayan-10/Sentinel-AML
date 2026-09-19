package com.meridiantrust.sentinel.alerting.service;

import com.meridiantrust.sentinel.alerting.model.AlertStatus;
import com.meridiantrust.sentinel.alerting.model.DispositionRecord;
import com.meridiantrust.sentinel.alerting.model.ProductivityMetrics;
import com.meridiantrust.sentinel.alerting.repository.AlertRepository;
import com.meridiantrust.sentinel.common.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Serves the analyst productivity metrics.
 *
 * <p>Thin by design: it fetches, then delegates every calculation to
 * {@link ProductivityCalculator}. Keeping the arithmetic out of the transactional
 * layer is what lets the arithmetic be unit-tested without a database.
 */
@Service
public class ProductivityService {

    private static final int DEFAULT_TREND_DAYS = 90;
    private static final int MAX_TREND_DAYS = 365;

    private final AlertRepository alertRepository;
    private final ProductivityCalculator calculator;

    public ProductivityService(AlertRepository alertRepository, ProductivityCalculator calculator) {
        this.alertRepository = alertRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(Roles.HAS_ANALYST)
    public ProductivityMetrics metrics(Integer trendDays) {
        int days = trendDays == null ? DEFAULT_TREND_DAYS : Math.min(Math.max(trendDays, 1), MAX_TREND_DAYS);

        List<DispositionRecord> disposed = alertRepository.findDispositionRecords();
        List<Instant> detections = alertRepository.findDetectionTimesSince(
                Instant.now().minus(Duration.ofDays(days)));

        return calculator.calculate(
                alertRepository.count(),
                alertRepository.countByStatus(AlertStatus.OPEN),
                disposed,
                detections);
    }
}
