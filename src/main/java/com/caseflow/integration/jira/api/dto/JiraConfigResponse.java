package com.caseflow.integration.jira.api.dto;

import com.caseflow.integration.jira.domain.JiraConfig;

import java.time.Instant;

public record JiraConfigResponse(
        Long id,
        boolean enabled,
        String baseUrl,
        String authType,
        String username,
        /** Always "****" — never expose the real token. */
        String apiToken,
        String projectKey,
        String issueType,
        String defaultLabels,
        String appBaseUrl,
        Instant updatedAt
) {
    public static JiraConfigResponse from(JiraConfig c) {
        return new JiraConfigResponse(
                c.getId(),
                Boolean.TRUE.equals(c.getIsEnabled()),
                c.getBaseUrl(),
                c.getAuthType(),
                c.getUsername(),
                c.getApiToken() != null && !c.getApiToken().isBlank() ? "****" : null,
                c.getProjectKey(),
                c.getIssueType(),
                c.getDefaultLabels(),
                c.getAppBaseUrl(),
                c.getUpdatedAt()
        );
    }
}
