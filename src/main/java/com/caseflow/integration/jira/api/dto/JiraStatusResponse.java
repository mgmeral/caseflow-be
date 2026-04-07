package com.caseflow.integration.jira.api.dto;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.jira.domain.TicketJiraLink;

import java.time.Instant;

/**
 * Combined Jira status response for a ticket — shows both the job state
 * and the resulting link (once created).
 */
public record JiraStatusResponse(
        Long jobId,
        String jobStatus,
        Integer attemptCount,
        String lastError,
        Instant nextAttemptAt,
        // Populated once SUCCEEDED
        String jiraIssueKey,
        String jiraUrl,
        Instant linkedAt
) {
    /** Build from an active/terminal job with no link yet. */
    public static JiraStatusResponse fromJob(IntegrationJob job) {
        return new JiraStatusResponse(
                job.getId(),
                job.getStatus().name(),
                job.getAttemptCount(),
                job.getStatus() == IntegrationJobStatus.FAILED
                        || job.getStatus() == IntegrationJobStatus.PERMANENTLY_FAILED
                        ? job.getLastError() : null,
                job.getStatus() == IntegrationJobStatus.FAILED ? job.getNextAttemptAt() : null,
                null, null, null
        );
    }

    /** Build when the link exists. */
    public static JiraStatusResponse fromLink(TicketJiraLink link, IntegrationJob job) {
        return new JiraStatusResponse(
                job != null ? job.getId() : null,
                IntegrationJobStatus.SUCCEEDED.name(),
                job != null ? job.getAttemptCount() : null,
                null,
                null,
                link.getJiraIssueKey(),
                link.getJiraUrl(),
                link.getCreatedAt()
        );
    }

    public static JiraStatusResponse notFound() {
        return new JiraStatusResponse(null, "NOT_REQUESTED", 0, null, null, null, null, null);
    }
}
