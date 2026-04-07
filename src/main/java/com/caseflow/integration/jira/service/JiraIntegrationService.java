package com.caseflow.integration.jira.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.jira.domain.TicketJiraLink;
import com.caseflow.integration.jira.repository.TicketJiraLinkRepository;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates Jira issue creation requests for tickets.
 *
 * <p>Validates authorization preconditions (ticket access, Jira config enabled,
 * duplicate guard) then delegates to the durable integration job queue.
 */
@Service
public class JiraIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(JiraIntegrationService.class);

    private final IntegrationJobService jobService;
    private final JiraConfigService jiraConfigService;
    private final TicketJiraLinkRepository linkRepository;
    private final TicketRepository ticketRepository;
    private final TicketHistoryService historyService;

    public JiraIntegrationService(IntegrationJobService jobService,
                                  JiraConfigService jiraConfigService,
                                  TicketJiraLinkRepository linkRepository,
                                  TicketRepository ticketRepository,
                                  TicketHistoryService historyService) {
        this.jobService = jobService;
        this.jiraConfigService = jiraConfigService;
        this.linkRepository = linkRepository;
        this.ticketRepository = ticketRepository;
        this.historyService = historyService;
    }

    /**
     * Requests Jira issue creation for the given ticket.
     *
     * <p>Idempotent: if a Jira link already exists, or a non-terminal create job is
     * already queued, returns the existing job without creating a duplicate.
     *
     * @return the existing or newly-created integration job
     * @throws IllegalStateException if Jira is not configured/enabled
     * @throws TicketNotFoundException if ticket does not exist
     */
    @Transactional
    public IntegrationJob requestJiraCreate(UUID ticketPublicId, Long requestedBy) {
        Ticket ticket = ticketRepository.findByPublicId(ticketPublicId)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket not found: " + ticketPublicId));

        // Guard: ticket must not already have a Jira link
        if (linkRepository.existsByTicketId(ticket.getId())) {
            throw new IllegalStateException(
                    "Ticket " + ticket.getTicketNo() + " already has a Jira issue linked");
        }

        // Guard: no active (non-terminal) job already in flight
        if (jobService.hasActiveJob(ticket.getId(), IntegrationType.JIRA_ISSUE_CREATE)) {
            throw new IllegalStateException(
                    "A Jira create request is already pending for ticket " + ticket.getTicketNo());
        }

        // Validate Jira config exists and is enabled (throws if not)
        jiraConfigService.requireEnabledConfig();

        String idempotencyKey = "JIRA_CREATE:" + ticket.getId();

        IntegrationJob job = jobService.enqueue(
                IntegrationType.JIRA_ISSUE_CREATE,
                ticket.getId(),
                ticket.getPublicId(),
                ticket.getCustomerId(),
                null,
                null,
                idempotencyKey,
                "USER",
                requestedBy
        );

        historyService.recordJiraCreateRequested(ticket.getId(), ticket.getPublicId(),
                job.getId(), requestedBy);

        log.info("Jira create requested — ticket: {}, jobId: {}, requestedBy: {}",
                ticket.getTicketNo(), job.getId(), requestedBy);
        return job;
    }

    @Transactional(readOnly = true)
    public Optional<TicketJiraLink> getJiraLink(UUID ticketPublicId) {
        return linkRepository.findByTicketPublicId(ticketPublicId);
    }

    @Transactional(readOnly = true)
    public Optional<IntegrationJob> getActiveJob(UUID ticketPublicId) {
        Ticket ticket = ticketRepository.findByPublicId(ticketPublicId).orElse(null);
        if (ticket == null) return Optional.empty();

        return jobService.findByIdempotencyKey("JIRA_CREATE:" + ticket.getId());
    }

    /**
     * Retries a FAILED Jira create job for the given ticket.
     */
    @Transactional
    public IntegrationJob retryJiraCreate(UUID ticketPublicId, Long requestedBy) {
        Ticket ticket = ticketRepository.findByPublicId(ticketPublicId)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket not found: " + ticketPublicId));

        IntegrationJob job = jobService.findByIdempotencyKey("JIRA_CREATE:" + ticket.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No Jira create job found for ticket " + ticket.getTicketNo()));

        jobService.resetForRetry(job);
        log.info("Jira create retry requested — ticket: {}, jobId: {}", ticket.getTicketNo(), job.getId());
        return job;
    }
}
