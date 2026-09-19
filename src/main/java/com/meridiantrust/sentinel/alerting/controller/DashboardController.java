package com.meridiantrust.sentinel.alerting.controller;

import com.meridiantrust.sentinel.customer.model.Customer;

import com.meridiantrust.sentinel.alerting.repository.AlertRepository;
import com.meridiantrust.sentinel.alerting.model.AlertStatus;
import com.meridiantrust.sentinel.casemanagement.repository.CaseRepository;
import com.meridiantrust.sentinel.casemanagement.model.CaseStatus;
import com.meridiantrust.sentinel.common.model.Severity;
import com.meridiantrust.sentinel.customer.repository.CustomerRepository;
import com.meridiantrust.sentinel.detection.service.DetectionEngine;
import com.meridiantrust.sentinel.transaction.repository.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates backing the analyst dashboard — KPI tiles and the risk heatmap.
 *
 * <p>Computed by the database rather than by fetching alerts and counting them
 * in Java. At alert volumes that matter, the second approach moves the whole
 * table over the wire to produce six numbers.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Aggregate metrics for the analyst dashboard")
public class DashboardController {

    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final DetectionEngine detectionEngine;

    public DashboardController(AlertRepository alertRepository,
                               CaseRepository caseRepository,
                               TransactionRepository transactionRepository,
                               CustomerRepository customerRepository,
                               DetectionEngine detectionEngine) {
        this.alertRepository = alertRepository;
        this.caseRepository = caseRepository;
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.detectionEngine = detectionEngine;
    }

    @GetMapping("/stats")
    @Operation(summary = "Dashboard KPIs: alert counts by status, severity and rule")
    public StatsDto stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (AlertStatus status : AlertStatus.values()) {
            byStatus.put(status.name(), alertRepository.countByStatus(status));
        }
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity.name(), alertRepository.countBySeverity(severity));
        }
        Map<String, Long> byRule = new LinkedHashMap<>();
        for (Object[] row : alertRepository.countByRule()) {
            byRule.put((String) row[0], (Long) row[1]);
        }
        Map<String, Long> caseStatus = new LinkedHashMap<>();
        for (CaseStatus status : CaseStatus.values()) {
            caseStatus.put(status.name(), caseRepository.countByStatus(status));
        }

        return new StatsDto(
                alertRepository.count(),
                caseRepository.count(),
                transactionRepository.countAll(),
                customerRepository.count(),
                byStatus, bySeverity, byRule, caseStatus,
                detectionEngine.activeRuleCodes());
    }

    @GetMapping("/heatmap")
    @Operation(summary = "Risk heatmap",
            description = "Customer x typology grid with the peak risk score in each cell.")
    public List<HeatmapCell> heatmap() {
        List<HeatmapCell> cells = new ArrayList<>();
        for (Object[] row : alertRepository.heatmapData()) {
            cells.add(new HeatmapCell(
                    (String) row[0],
                    (String) row[1],
                    ((Number) row[2]).intValue(),
                    ((Number) row[3]).longValue()));
        }
        return cells;
    }

    public record StatsDto(long totalAlerts,
                           long totalCases,
                           long totalTransactions,
                           long totalCustomers,
                           Map<String, Long> alertsByStatus,
                           Map<String, Long> alertsBySeverity,
                           Map<String, Long> alertsByRule,
                           Map<String, Long> casesByStatus,
                           List<String> activeRules) {}

    public record HeatmapCell(String customerId, String typology, int peakRiskScore, long alertCount) {}
}
