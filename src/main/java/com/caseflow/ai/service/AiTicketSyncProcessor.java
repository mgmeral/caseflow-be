package com.caseflow.ai.service;

import com.caseflow.ai.client.AiServiceUnavailableException;
import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.response.AiIngestResponse;
import com.caseflow.ai.context.TicketAiContextBuilder;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobProcessor;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Processes {@link IntegrationType#AI_TICKET_SYNC} jobs by reconciling caseflow-ai-service's
 * index with the ticket as it is <em>now</em>: RESOLVED/CLOSED tickets are (re)ingested,
 * anything else — including a ticket that no longer exists — is removed from the index.
 *
 * <p>An AI-service failure is a retryable job failure (the worker backs off); a 4xx from the
 * AI service means the request itself is wrong, so it fails permanently.
 */
@Component
public class AiTicketSyncProcessor implements IntegrationJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiTicketSyncProcessor.class);

    private final CaseflowAiClient aiClient;
    private final TicketAiContextBuilder contextBuilder;
    private final TicketRepository ticketRepository;
    private final TicketAiIndexRepository indexRepository;
    private final IntegrationJobService jobService;

    public AiTicketSyncProcessor(CaseflowAiClient aiClient,
                                 TicketAiContextBuilder contextBuilder,
                                 TicketRepository ticketRepository,
                                 TicketAiIndexRepository indexRepository,
                                 IntegrationJobService jobService) {
        this.aiClient = aiClient;
        this.contextBuilder = contextBuilder;
        this.ticketRepository = ticketRepository;
        this.indexRepository = indexRepository;
        this.jobService = jobService;
    }

    @Override
    public IntegrationType supportedType() {
        return IntegrationType.AI_TICKET_SYNC;
    }

    @Override
    public void process(IntegrationJob job) throws IntegrationJobExecutionException {
        String correlationId = "ai-ticket-sync-" + job.getId();
        Optional<Ticket> found = ticketRepository.findById(job.getTicketId());
        try {
            if (found.isPresent() && AiTicketSyncService.INDEXED_STATUSES.contains(found.get().getStatus())) {
                Ticket ticket = found.get();
                AiIngestResponse res = aiClient.ingestTicket(contextBuilder.buildIngestRequest(ticket), correlationId);
                if ("FAILED".equals(res.status())) {
                    throw new IntegrationJobExecutionException("AI ingest failed: " + res.message(), false);
                }
                recordIndexed(ticket.getId());
                jobService.markSucceeded(job, "INGESTED:" + res.chunksIndexed());
                log.info("AI ticket sync — ingested [ticketId={}, chunks={}]", ticket.getId(), res.chunksIndexed());
            } else {
                String publicId = found.map(t -> t.getPublicId().toString())
                        .orElse(job.getTicketPublicId() != null ? job.getTicketPublicId().toString() : null);
                if (publicId == null) {
                    throw new IntegrationJobExecutionException("Ticket " + job.getTicketId()
                            + " no longer exists and the job has no publicId to remove", true);
                }
                aiClient.deleteSource("TICKET", publicId, correlationId);
                jobService.markSucceeded(job, "REMOVED");
                log.info("AI ticket sync — removed from index [ticketId={}]", job.getTicketId());
            }
        } catch (AiServiceUnavailableException ex) {
            boolean clientError = ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
            throw new IntegrationJobExecutionException(ex.getMessage(), clientError, ex);
        }
    }

    private void recordIndexed(Long ticketId) {
        indexRepository.findByTicketId(ticketId).ifPresent(index -> {
            index.markSynced(index.getSourceVersion(), null);
            indexRepository.save(index);
        });
    }
}
