package com.caseflow.ai.service;

import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.repository.IntegrationJobRepository;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Keeps caseflow-ai-service's similar-case index in step with tickets, over the durable
 * {@code integration_jobs} queue (REST; the Kafka lane stays off — ADR-0004).
 *
 * <p>A sync job carries no payload: {@link AiTicketSyncProcessor} reads the ticket's state when
 * it runs and either indexes it (RESOLVED/CLOSED) or removes it. Jobs may therefore run in any
 * order and retry freely — the last one to run always reflects the current ticket.
 */
@Service
public class AiTicketSyncService {

    private static final Logger log = LoggerFactory.getLogger(AiTicketSyncService.class);

    /** Only settled tickets are useful as "similar cases" — they carry a resolution. */
    public static final Set<TicketStatus> INDEXED_STATUSES = Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private static final int REINDEX_PAGE_SIZE = 200;

    private final IntegrationJobService jobService;
    private final IntegrationJobRepository jobRepository;
    private final TicketRepository ticketRepository;
    private final AiAvailabilityService availabilityService;

    public AiTicketSyncService(IntegrationJobService jobService,
                               IntegrationJobRepository jobRepository,
                               TicketRepository ticketRepository,
                               AiAvailabilityService availabilityService) {
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.ticketRepository = ticketRepository;
        this.availabilityService = availabilityService;
    }

    /**
     * Enqueues a sync for the ticket, unless one is already waiting to run (it will read the
     * same current state). A job already PROCESSING does not count — it may have read the
     * state before this change.
     *
     * @param triggeredByType {@code integration_jobs.triggered_by_type}: EVENT, SYSTEM, ...
     * @return true if a job was enqueued
     */
    @Transactional
    public boolean requestSync(Ticket ticket, String triggeredByType) {
        if (!availabilityService.isAvailable()) {
            return false;
        }
        if (jobRepository.existsByTicketIdAndIntegrationTypeAndStatus(
                ticket.getId(), IntegrationType.AI_TICKET_SYNC, IntegrationJobStatus.PENDING)) {
            return false;
        }
        jobService.enqueue(IntegrationType.AI_TICKET_SYNC, ticket.getId(), ticket.getPublicId(),
                ticket.getCustomerId(), null, null,
                "ai-ticket-sync:" + ticket.getId() + ":" + UUID.randomUUID(),
                triggeredByType, null);
        log.debug("AI ticket sync enqueued [ticketId={}, trigger={}]", ticket.getId(), triggeredByType);
        return true;
    }

    /**
     * Enqueues a sync for every RESOLVED/CLOSED ticket — the one-off backfill after the vector
     * collection is (re)created. Safe to repeat: re-ingesting a ticket replaces its chunks.
     *
     * @return number of jobs enqueued
     */
    @Transactional
    public int reindexAll() {
        int enqueued = 0;
        for (TicketStatus status : INDEXED_STATUSES) {
            int page = 0;
            Page<Ticket> batch;
            do {
                batch = ticketRepository.findByStatus(status, PageRequest.of(page++, REINDEX_PAGE_SIZE));
                for (Ticket ticket : batch) {
                    if (requestSync(ticket, "SYSTEM")) enqueued++;
                }
            } while (batch.hasNext());
        }
        log.info("AI reindex requested — {} ticket sync jobs enqueued", enqueued);
        return enqueued;
    }
}
