package com.caseflow.integration.jira.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JiraConfigRequest(
        @NotBlank @Size(max = 1000) String baseUrl,
        @Size(max = 50) String authType,
        @Size(max = 500) String username,
        /** Omit or send blank to preserve the existing token. */
        String apiToken,
        @NotBlank @Size(max = 100) String projectKey,
        @Size(max = 100) String issueType,
        @Size(max = 500) String defaultLabels,
        @Size(max = 1000) String appBaseUrl,
        boolean enabled
) {}
