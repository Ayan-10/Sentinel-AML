package com.meridiantrust.sentinel.alerting.repository;

import com.meridiantrust.sentinel.alerting.model.Alert;
import com.meridiantrust.sentinel.alerting.model.AlertStatus;

import com.meridiantrust.sentinel.alerting.model.DispositionRecord;
import com.meridiantrust.sentinel.common.model.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByAlertRef(String alertRef);

    Optional<Alert> findByDedupKey(String dedupKey);

    List<Alert> findByCaseId(Long caseId);

    List<Alert> findByCustomerIdOrderByRiskScoreDesc(String customerId);

    /**
     * The analyst queue. Business rule 7 requires higher-risk alerts to sort to
     * the top; every filter is optional so one query backs the whole queue UI
     * rather than a combinatorial set of finder methods.
     */
    @Query("""
           select a from Alert a
           where (:status   is null or a.status   = :status)
             and (:severity is null or a.severity = :severity)
             and (:ruleCode is null or a.ruleCode = :ruleCode)
             and (:customerId is null or a.customerId = :customerId)
             and (:minScore is null or a.riskScore >= :minScore)
           """)
    Page<Alert> search(@Param("status") AlertStatus status,
                       @Param("severity") Severity severity,
                       @Param("ruleCode") String ruleCode,
                       @Param("customerId") String customerId,
                       @Param("minScore") Integer minScore,
                       Pageable pageable);

    long countByStatus(AlertStatus status);

    long countBySeverity(Severity severity);

    @Query("select a.ruleCode, count(a) from Alert a group by a.ruleCode order by count(a) desc")
    List<Object[]> countByRule();

    @Query("""
           select a.customerId, a.typology, max(a.riskScore), count(a)
           from Alert a
           group by a.customerId, a.typology
           """)
    List<Object[]> heatmapData();

    /**
     * Disposed alerts only, projected to what the productivity calculation
     * needs. Restricting to disposed rows keeps this proportional to the work
     * actually completed rather than to the whole alert table.
     */
    @Query("select new com.meridiantrust.sentinel.alerting.model.DispositionRecord("
         + "a.ruleCode, a.disposition, a.firstDetectedAt, a.disposedAt) "
         + "from Alert a where a.disposition is not null")
    List<DispositionRecord> findDispositionRecords();

    /**
     * Detection timestamps for the volume trend, bounded by a window so the
     * result set stays proportional to the reporting period rather than to the
     * lifetime of the system.
     */
    @Query("select a.firstDetectedAt from Alert a where a.firstDetectedAt >= :from")
    List<Instant> findDetectionTimesSince(@Param("from") Instant from);
}
