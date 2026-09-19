package com.meridiantrust.sentinel.common.audit.service;

import com.meridiantrust.sentinel.common.audit.model.AuditLog;
import com.meridiantrust.sentinel.common.audit.repository.AuditLogRepository;

import com.meridiantrust.sentinel.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the immutable audit trail.
 *
 * <p>{@code REQUIRES_NEW} on the write is a deliberate choice: an audit record
 * must survive even when the business transaction that produced it later rolls
 * back. Losing the evidence that someone <em>attempted</em> a transition would
 * defeat the purpose of the trail in a regulated system.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, String entityId, String action,
                       String fromState, String toState, String details) {
        AuditLog entry = new AuditLog(entityType, entityId, action,
                currentUser.username(), currentUser.roles(), fromState, toState, details);
        repository.save(entry);
        log.info("AUDIT {} {} [{}] {} -> {} by {}",
                entityType, entityId, action, fromState, toState, entry.getActor());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityType, String entityId, String action, String details) {
        record(entityType, entityId, action, null, null, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> trailFor(String entityType, String entityId, Pageable pageable) {
        return repository.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> trail(String entityType, Pageable pageable) {
        return entityType == null || entityType.isBlank()
                ? repository.findAllByOrderByOccurredAtDesc(pageable)
                : repository.findByEntityTypeOrderByOccurredAtDesc(entityType, pageable);
    }
}
