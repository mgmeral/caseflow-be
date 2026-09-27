package com.caseflow.integration.jira.api.dto;

import com.caseflow.integration.jira.service.JiraApiClient;

import java.util.List;

/**
 * Result of {@code POST /api/admin/integrations/jira/test}.
 *
 * @param success    every check passed
 * @param message    one-line summary (kept for older clients)
 * @param checks     AUTH, PROJECT and ISSUE_TYPE in order; later ones are "Skipped" after a failure
 * @param issueTypes issue types the Jira account may create in the project (empty unless reached)
 */
public record JiraTestResponse(
        boolean success,
        String message,
        List<JiraApiClient.JiraCheck> checks,
        List<String> issueTypes
) {
    public static JiraTestResponse from(JiraApiClient.JiraDiagnostics d) {
        String message = d.success()
                ? "Connection works — CaseFlow can create issues with these settings."
                : d.checks().stream().filter(c -> !c.ok()).findFirst()
                        .map(JiraApiClient.JiraCheck::message).orElse("Jira check failed.");
        return new JiraTestResponse(d.success(), message, d.checks(), d.issueTypes());
    }

    public static JiraTestResponse failure(String message) {
        return new JiraTestResponse(false, message, List.of(), List.of());
    }
}
