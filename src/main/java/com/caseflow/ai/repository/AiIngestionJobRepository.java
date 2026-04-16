package com.caseflow.ai.repository;

import com.caseflow.ai.domain.AiIngestionJob;
import com.caseflow.ai.domain.AiSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiIngestionJobRepository extends JpaRepository<AiIngestionJob, Long> {

    Optional<AiIngestionJob> findByJobId(String jobId);

    Optional<AiIngestionJob> findByCorrelationId(String correlationId);

    List<AiIngestionJob> findByEntityTypeAndEntityId(String entityType, Long entityId);

    List<AiIngestionJob> findByStatus(AiSyncStatus status);
}
