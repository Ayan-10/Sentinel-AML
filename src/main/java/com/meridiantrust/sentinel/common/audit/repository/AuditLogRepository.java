package com.meridiantrust.sentinel.common.audit.repository;

import com.meridiantrust.sentinel.common.audit.model.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Deliberately narrowed repository — Interface Segregation applied to
 * persistence.
 *
 * <p>This extends {@link Repository} rather than {@code JpaRepository} for one
 * specific reason: {@code JpaRepository} would inherit {@code delete},
 * {@code deleteAll} and {@code saveAndFlush}-driven updates. By declaring only
 * the operations an append-only log may perform, tampering with the audit trail
 * is not "against policy" — there is simply no method to call. Immutability
 * enforced by the type system beats immutability enforced by code review.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    /** Bulk append, for backfilling synthetic history. Still append-only. */
    List<AuditLog> saveAll(Iterable<AuditLog> entries);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, String entityId, Pageable pageable);

    Page<AuditLog> findByEntityTypeOrderByOccurredAtDesc(String entityType, Pageable pageable);

    Page<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);

    long count();
}
