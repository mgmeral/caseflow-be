package com.caseflow.ai.orchestration;

import com.caseflow.ai.domain.AiIngestionJob;
import com.caseflow.ai.domain.AiSyncStatus;
import com.caseflow.ai.domain.TicketAiIndex;
import com.caseflow.ai.event.AiEventPublisher;
import com.caseflow.ai.event.dto.AiSyncEvent;
import com.caseflow.ai.repository.AiIngestionJobRepository;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.ai.service.AiSourceVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates async AI sync event publishing and job tracking.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Triggers AI source version increment on AI-relevant ticket mutations</li>
 *   <li>Creates an {@link AiIngestionJob} to track the request</li>
 *   <li>Publishes via {@link AiEventPublisher} (Kafka or no-op depending on config)</li>
 *   <li>Updates {@link TicketAiIndex} state to PENDING after publishing</li>
 * </ul>
 *
 * <h2>Safety rules</h2>
 * <ul>
 *   <li>Never throws on Kafka failures — the job row records intent</li>
 *   <li>Safe when async is disabled — no-op publisher logs and returns</li>
 *   <li>Does NOT block the calling ticket workflow</li>
 * </ul>
 */
@Service
public class AiIngestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AiIngestOrchestrator.class);

    private static final String ENTITY_TICKET = "TICKET";
    private static final String ENTITY_POLICY = "POLICY";
    private static final String ENTITY_TEMPLATE = "TEMPLATE";

    private final AiEventPublisher eventPublisher;
    private final AiSourceVersionService sourceVersionService;
    private final TicketAiIndexRepository indexRepository;
    private final AiIngestionJobRepository jobRepository;

    public AiIngestOrchestrator(AiEventPublisher eventPublisher,
                                 AiSourceVersionService sourceVersionService,
                                 TicketAiIndexRepository indexRepository,
                                 AiIngestionJobRepository jobRepository) {
        this.eventPublisher = eventPublisher;
        this.sourceVersionService = sourceVersionService;
        this.indexRepository = indexRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Called when AI-relevant ticket content changes.
     * Increments source version, creates an ingestion job, and publishes the sync event.
     *
     * @param ticketId   ticket whose AI-relevant data changed
     * @param requestedBy user ID or system actor triggering the sync
     */
    @Transactional
    public void requestTicketSync(Long ticketId, String requestedBy) {
        // 1. Increment source version + invalidate cache
        sourceVersionService.onAiRelevantChange(ticketId);
        long sourceVersion = sourceVersionService.currentSourceVersion(ticketId);

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 2. Record the sync job
        AiIngestionJob job = new AiIngestionJob();
        job.setJobId(eventId);
        job.setEntityType(ENTITY_TICKET);
        job.setEntityId(ticketId);
        job.setSourceVersion(sourceVersion);
        job.setCorrelationId(correlationId);
        job.setStatus(AiSyncStatus.PENDING);

        // 3. Update index state
        TicketAiIndex index = indexRepository.findByTicketId(ticketId).orElse(null);
        if (index != null) {
            index.markPending(eventId, correlationId);
        }

        if (eventPublisher.isActive()) {
            job.setTopic("ticket-ai-sync-requested");
            jobRepository.save(job);
            if (index != null) indexRepository.save(index);

            AiSyncEvent event = new AiSyncEvent(
                    eventId, correlationId, ENTITY_TICKET, ticketId, sourceVersion,
                    "SYNC", Instant.now(), requestedBy);
            eventPublisher.publishTicketSyncRequested(event);

            log.info("Ticket AI sync requested [ticketId={}, sourceVersion={}, correlationId={}]",
                    ticketId, sourceVersion, correlationId);
        } else {
            // Async is disabled — mark as SKIPPED so the state is honest
            job.setStatus(AiSyncStatus.SKIPPED);
            jobRepository.save(job);
            if (index != null) {
                index.markSkipped();
                indexRepository.save(index);
            }
            log.debug("Ticket AI sync skipped (async disabled) [ticketId={}, sourceVersion={}]",
                    ticketId, sourceVersion);
        }
    }

    /**
     * Requests a policy document to be ingested into the AI service's knowledge base.
     */
    @Transactional
    public void requestPolicyIngest(Long policyId, long version, String requestedBy) {
        if (!eventPublisher.isActive()) {
            log.warn("Policy AI ingest skipped (async disabled) [policyId={}]", policyId);
            return;
        }

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        AiIngestionJob job = new AiIngestionJob();
        job.setJobId(eventId);
        job.setEntityType(ENTITY_POLICY);
        job.setEntityId(policyId);
        job.setSourceVersion(version);
        job.setCorrelationId(correlationId);
        job.setTopic("policy-ai-ingest-requested");
        job.setStatus(AiSyncStatus.PENDING);
        jobRepository.save(job);

        AiSyncEvent event = new AiSyncEvent(
                eventId, correlationId, ENTITY_POLICY, policyId, version,
                "INGEST", Instant.now(), requestedBy);
        eventPublisher.publishPolicyIngestRequested(event);

        log.info("Policy AI ingest requested [policyId={}, version={}, correlationId={}]",
                policyId, version, correlationId);
    }

    /**
     * Requests a mail template to be ingested into the AI service's knowledge base.
     */
    @Transactional
    public void requestTemplateIngest(Long templateId, long version, String requestedBy) {
        if (!eventPublisher.isActive()) {
            log.warn("Template AI ingest skipped (async disabled) [templateId={}]", templateId);
            return;
        }

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        AiIngestionJob job = new AiIngestionJob();
        job.setJobId(eventId);
        job.setEntityType(ENTITY_TEMPLATE);
        job.setEntityId(templateId);
        job.setSourceVersion(version);
        job.setCorrelationId(correlationId);
        job.setTopic("template-ai-ingest-requested");
        job.setStatus(AiSyncStatus.PENDING);
        jobRepository.save(job);

        AiSyncEvent event = new AiSyncEvent(
                eventId, correlationId, ENTITY_TEMPLATE, templateId, version,
                "INGEST", Instant.now(), requestedBy);
        eventPublisher.publishTemplateIngestRequested(event);

        log.info("Template AI ingest requested [templateId={}, version={}, correlationId={}]",
                templateId, version, correlationId);
    }
}
