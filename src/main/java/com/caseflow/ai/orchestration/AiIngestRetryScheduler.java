package com.caseflow.ai.orchestration;

import com.caseflow.ai.domain.AiIngestionJob;
import com.caseflow.ai.domain.AiSyncStatus;
import com.caseflow.ai.event.AiEventPublisher;
import com.caseflow.ai.event.dto.AiSyncEvent;
import com.caseflow.ai.repository.AiIngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Periodically retries FAILED AI ingestion jobs when Kafka is enabled.
 *
 * <p>Only active when {@code caseflow.ai.async.enabled=true}. Picks up FAILED
 * TICKET sync jobs with retryCount below the configured maximum and re-publishes
 * them. The retry count on the existing job row is incremented each attempt so
 * perpetual retry loops are prevented.
 */
@Component
@ConditionalOnProperty(name = "caseflow.ai.async.enabled", havingValue = "true")
public class AiIngestRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AiIngestRetryScheduler.class);
    private static final String ENTITY_TICKET = "TICKET";

    private final AiIngestionJobRepository jobRepository;
    private final AiEventPublisher eventPublisher;

    @Value("${caseflow.ai.async.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${caseflow.ai.async.kafka.topic.ticket-sync:ticket-ai-sync-requested}")
    private String ticketSyncTopic;

    public AiIngestRetryScheduler(AiIngestionJobRepository jobRepository,
                                   AiEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${caseflow.ai.async.retry.interval-ms:300000}")
    @Transactional
    public void retryFailedJobs() {
        if (!eventPublisher.isActive()) return;

        List<AiIngestionJob> failed = jobRepository
                .findByStatusAndEntityTypeAndRetryCountLessThan(
                        AiSyncStatus.FAILED, ENTITY_TICKET, maxRetryAttempts);

        if (failed.isEmpty()) return;

        log.info("AI ingest retry — found {} FAILED ticket sync job(s) eligible for retry", failed.size());

        for (AiIngestionJob job : failed) {
            try {
                job.setRetryCount(job.getRetryCount() + 1);
                job.setStatus(AiSyncStatus.PENDING);
                job.setErrorMessage(null);
                jobRepository.save(job);

                AiSyncEvent event = new AiSyncEvent(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        job.getEntityType(),
                        job.getEntityId(),
                        job.getSourceVersion(),
                        "SYNC",
                        Instant.now(),
                        "system-retry"
                );
                eventPublisher.publishTicketSyncRequested(event);

                log.info("AI ingest retry published [jobId={}, entityId={}, attempt={}]",
                        job.getJobId(), job.getEntityId(), job.getRetryCount());

            } catch (Exception ex) {
                log.error("AI ingest retry failed [jobId={}, entityId={}]: {}",
                        job.getJobId(), job.getEntityId(), ex.getMessage());
                job.setStatus(AiSyncStatus.FAILED);
                job.setErrorMessage("Retry failed: " + ex.getMessage());
                jobRepository.save(job);
            }
        }
    }
}
