package com.caseflow.security.audit;

import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records security-significant events to the audit log.
 *
 * <p>All writes are async and use REQUIRES_NEW so audit log entries are
 * committed regardless of whether the calling transaction rolls back.
 */
@Service
public class SecurityAuditService {

    private final SecurityAuditLogRepository repository;

    public SecurityAuditService(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityAuditEventType eventType,
                       Long userId, String username,
                       String ipAddress, String userAgent,
                       boolean success, String failureReason) {
        SecurityAuditLog entry = new SecurityAuditLog();
        entry.setEventType(eventType);
        entry.setActorUserId(userId);
        entry.setActorUsername(username);
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(truncate(userAgent, 500));
        entry.setOutcome(success ? "SUCCESS" : "FAILURE");
        entry.setFailureReason(truncate(failureReason, 200));
        entry.setCorrelationId(MDC.get("correlationId"));
        repository.save(entry);
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
