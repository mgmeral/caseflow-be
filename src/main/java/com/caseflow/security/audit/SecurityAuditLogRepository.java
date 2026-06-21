package com.caseflow.security.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    List<SecurityAuditLog> findByActorUserIdOrderByCreatedAtDesc(Long userId);

    List<SecurityAuditLog> findByEventTypeOrderByCreatedAtDesc(SecurityAuditEventType eventType);
}
