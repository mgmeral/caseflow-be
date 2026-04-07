package com.caseflow.integration.jira.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.jira.api.dto.JiraStatusResponse;
import com.caseflow.integration.jira.domain.TicketJiraLink;
import com.caseflow.integration.jira.service.JiraIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * Ticket-scoped Jira integration endpoints.
 *
 * <p>Authorization: requires TICKET_READ scope for GET; requires CUSTOMER_REPLY_SEND
 * (i.e. agent-level access) for create/retry actions.
 */
@RestController
@RequestMapping("/api/tickets/{ticketPublicId}/jira")
public class JiraController {

    private final JiraIntegrationService jiraService;

    public JiraController(JiraIntegrationService jiraService) {
        this.jiraService = jiraService;
    }

    /**
     * Returns the current Jira status for the ticket (link, job state, or NOT_REQUESTED).
     */
    @GetMapping
    @PreAuthorize("@ticketAuth.canReadTicketByPublicId(authentication, #ticketPublicId)")
    public ResponseEntity<JiraStatusResponse> getJiraStatus(
            @PathVariable UUID ticketPublicId) {

        Optional<TicketJiraLink> link = jiraService.getJiraLink(ticketPublicId);
        if (link.isPresent()) {
            Optional<IntegrationJob> job = jiraService.getActiveJob(ticketPublicId);
            return ResponseEntity.ok(JiraStatusResponse.fromLink(link.get(), job.orElse(null)));
        }

        Optional<IntegrationJob> job = jiraService.getActiveJob(ticketPublicId);
        if (job.isPresent()) {
            return ResponseEntity.ok(JiraStatusResponse.fromJob(job.get()));
        }

        return ResponseEntity.ok(JiraStatusResponse.notFound());
    }

    /**
     * Enqueues a Jira issue creation request for the given ticket.
     * Returns 409 if a link or active job already exists.
     */
    @PostMapping("/create")
    @PreAuthorize("@ticketAuth.canSendCustomerReply(authentication, #ticketPublicId) "
            + "or hasAuthority('PERM_INTEGRATION_CONFIG_MANAGE')")
    public ResponseEntity<JiraStatusResponse> createJiraIssue(
            @PathVariable UUID ticketPublicId,
            @AuthenticationPrincipal CaseFlowUserDetails user) {

        IntegrationJob job = jiraService.requestJiraCreate(ticketPublicId, user.getUserId());
        return ResponseEntity.accepted().body(JiraStatusResponse.fromJob(job));
    }

    /**
     * Retries a FAILED Jira create job.
     */
    @PostMapping("/retry")
    @PreAuthorize("@ticketAuth.canSendCustomerReply(authentication, #ticketPublicId) "
            + "or hasAuthority('PERM_INTEGRATION_CONFIG_MANAGE')")
    public ResponseEntity<JiraStatusResponse> retryJiraCreate(
            @PathVariable UUID ticketPublicId,
            @AuthenticationPrincipal CaseFlowUserDetails user) {

        IntegrationJob job = jiraService.retryJiraCreate(ticketPublicId, user.getUserId());
        return ResponseEntity.accepted().body(JiraStatusResponse.fromJob(job));
    }
}
