package com.caseflow.integration.jira.processor;

import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.domain.TicketJiraLink;
import com.caseflow.integration.jira.repository.TicketJiraLinkRepository;
import com.caseflow.integration.jira.service.JiraApiClient;
import com.caseflow.integration.jira.service.JiraConfigService;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobProcessor;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes {@link IntegrationType#JIRA_ISSUE_CREATE} integration jobs.
 *
 * <p>Loads the Jira config and ticket, calls the Jira REST API, persists the
 * {@link TicketJiraLink}, and records history events on success/failure.
 */
@Component
public class JiraJobProcessor implements IntegrationJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(JiraJobProcessor.class);

    private final JiraConfigService configService;
    private final JiraApiClient jiraApiClient;
    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final TicketJiraLinkRepository linkRepository;
    private final IntegrationJobService jobService;
    private final TicketHistoryService historyService;

    public JiraJobProcessor(JiraConfigService configService,
                             JiraApiClient jiraApiClient,
                             TicketRepository ticketRepository,
                             CustomerRepository customerRepository,
                             TicketJiraLinkRepository linkRepository,
                             IntegrationJobService jobService,
                             TicketHistoryService historyService) {
        this.configService = configService;
        this.jiraApiClient = jiraApiClient;
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.linkRepository = linkRepository;
        this.jobService = jobService;
        this.historyService = historyService;
    }

    @Override
    public IntegrationType supportedType() {
        return IntegrationType.JIRA_ISSUE_CREATE;
    }

    @Override
    @Transactional
    public void process(IntegrationJob job) throws IntegrationJobExecutionException {
        // Reload ticket — must still exist
        Ticket ticket = ticketRepository.findById(job.getTicketId())
                .orElseThrow(() -> new IntegrationJobExecutionException(
                        "Ticket " + job.getTicketId() + " no longer exists", true));

        // Guard: do not create a duplicate link
        if (linkRepository.existsByTicketId(ticket.getId())) {
            // Link already exists (possibly from a previous successful attempt that wasn't marked)
            // Treat as success to avoid re-creating.
            TicketJiraLink existing = linkRepository.findByTicketId(ticket.getId()).orElseThrow();
            jobService.markSucceeded(job, existing.getJiraIssueKey());
            log.info("Jira link already exists for ticket {} — job {} marked succeeded", ticket.getId(), job.getId());
            return;
        }

        JiraConfig config;
        try {
            config = configService.requireEnabledConfig();
        } catch (IllegalStateException e) {
            throw new IntegrationJobExecutionException(
                    "Jira not configured or disabled: " + e.getMessage(), true);
        }

        // Build a concise issue payload
        JiraApiClient.JiraIssuePayload payload = buildPayload(ticket, config);

        // Call Jira — may throw IntegrationJobExecutionException
        JiraApiClient.JiraIssueResult result;
        try {
            result = jiraApiClient.createIssue(config, payload);
        } catch (IntegrationJobExecutionException e) {
            // Record failure history before re-throwing (worker will handle status transition)
            historyService.recordJiraCreateFailed(ticket.getId(), ticket.getPublicId(),
                    job.getId(), e.getMessage(), e.isPermanent());
            throw e;
        }

        // Persist the link
        TicketJiraLink link = new TicketJiraLink();
        link.setTicketId(ticket.getId());
        link.setTicketPublicId(ticket.getPublicId());
        link.setJiraIssueKey(result.issueKey());
        link.setJiraIssueId(result.issueId());
        link.setJiraUrl(result.issueUrl());
        link.setIntegrationJobId(job.getId());
        link.setCreatedBy(job.getCreatedBy());
        linkRepository.save(link);

        // Mark job succeeded
        jobService.markSucceeded(job, result.issueKey());

        // Record history
        historyService.recordJiraIssueCreated(ticket.getId(), ticket.getPublicId(),
                job.getId(), result.issueKey(), result.issueUrl());

        log.info("Jira issue created — ticket: {}, issueKey: {}, url: {}",
                ticket.getTicketNo(), result.issueKey(), result.issueUrl());
    }

    private JiraApiClient.JiraIssuePayload buildPayload(Ticket ticket, JiraConfig config) {
        String customerName = resolveCustomerName(ticket.getCustomerId());

        String summary = "[" + ticket.getTicketNo() + "] " + truncate(ticket.getSubject(), 200);

        StringBuilder desc = new StringBuilder();
        desc.append("CaseFlow Ticket: ").append(ticket.getTicketNo()).append("\n");
        if (config.getAppBaseUrl() != null && !config.getAppBaseUrl().isBlank()) {
            desc.append("Link: ").append(config.getAppBaseUrl().replaceAll("/+$", ""))
                .append("/tickets/").append(ticket.getPublicId()).append("\n");
        }
        desc.append("\n");
        desc.append("Customer: ").append(customerName).append("\n");
        desc.append("Status: ").append(ticket.getStatus()).append("\n");
        desc.append("Priority: ").append(ticket.getPriority()).append("\n");
        if (ticket.getDescription() != null && !ticket.getDescription().isBlank()) {
            desc.append("\nDescription:\n").append(truncate(ticket.getDescription(), 1000));
        }

        return new JiraApiClient.JiraIssuePayload(summary, desc.toString());
    }

    private String resolveCustomerName(Long customerId) {
        if (customerId == null) return "N/A";
        return customerRepository.findById(customerId)
                .map(Customer::getName)
                .orElse("Unknown");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
