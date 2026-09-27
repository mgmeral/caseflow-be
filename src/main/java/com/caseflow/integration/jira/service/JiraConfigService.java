package com.caseflow.integration.jira.service;

import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.repository.JiraConfigRepository;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Manages the global Jira configuration.
 */
@Service
public class JiraConfigService {

    private final JiraConfigRepository configRepository;
    private final JiraApiClient jiraApiClient;

    public JiraConfigService(JiraConfigRepository configRepository,
                              JiraApiClient jiraApiClient) {
        this.configRepository = configRepository;
        this.jiraApiClient = jiraApiClient;
    }

    @Transactional(readOnly = true)
    public Optional<JiraConfig> findConfig() {
        return configRepository.findFirstByOrderByIdAsc();
    }

    /**
     * Returns the active Jira config, or throws if not configured.
     */
    @Transactional(readOnly = true)
    public JiraConfig requireEnabledConfig() {
        JiraConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Jira integration is not configured"));
        if (!Boolean.TRUE.equals(config.getIsEnabled())) {
            throw new IllegalStateException("Jira integration is disabled");
        }
        return config;
    }

    /**
     * Creates or updates the global Jira config.
     * If {@code apiToken} is blank in the request, preserves the existing token.
     */
    @Transactional
    public JiraConfig save(String baseUrl, String authType, String username, String apiToken,
                           String projectKey, String issueType, String defaultLabels,
                           String appBaseUrl, boolean enabled, Long updatedBy) {
        JiraConfig config = configRepository.findFirstByOrderByIdAsc().orElse(new JiraConfig());

        config.setBaseUrl(baseUrl.strip());
        config.setAuthType(authType != null ? authType : "BASIC");
        config.setUsername(username);
        if (apiToken != null && !apiToken.isBlank()) {
            config.setApiToken(apiToken);
        }
        config.setProjectKey(projectKey.strip());
        config.setIssueType(issueType != null ? issueType.strip() : "Task");
        config.setDefaultLabels(defaultLabels);
        config.setAppBaseUrl(appBaseUrl);
        config.setIsEnabled(enabled);
        config.setUpdatedBy(updatedBy);
        if (config.getCreatedBy() == null) config.setCreatedBy(updatedBy);

        return configRepository.save(config);
    }

    /**
     * Tests connectivity using the current saved config.
     *
     * @throws IllegalStateException if no config exists
     * @throws IntegrationJobExecutionException on connection failure
     */
    @Transactional(readOnly = true)
    public boolean testConnection() {
        JiraConfig config = configRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Jira integration is not configured"));
        return jiraApiClient.testConnection(config);
    }

    /**
     * Runs the step-by-step Jira check against the given, possibly unsaved, values — nothing is
     * persisted. A blank token reuses the saved one only when the site address is unchanged, so
     * a stored token is never sent to a different host.
     *
     * @throws IllegalStateException if no token is available for this address
     */
    @Transactional(readOnly = true)
    public JiraApiClient.JiraDiagnostics diagnose(String baseUrl, String username, String apiToken,
                                                  String projectKey, String issueType) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String token = apiToken != null && !apiToken.isBlank() ? apiToken : null;
        if (token == null) {
            token = configRepository.findFirstByOrderByIdAsc()
                    .filter(saved -> normalizeBaseUrl(saved.getBaseUrl()).equalsIgnoreCase(normalizedBase))
                    .map(JiraConfig::getApiToken)
                    .filter(t -> !t.isBlank())
                    .orElseThrow(() -> new IllegalStateException(
                            "Enter the API token — a saved token is only reused for the same Jira address."));
        }

        JiraConfig draft = new JiraConfig();
        draft.setBaseUrl(normalizedBase);
        draft.setAuthType("BASIC");
        draft.setUsername(username != null ? username.strip() : null);
        draft.setApiToken(token);
        draft.setProjectKey(projectKey.strip());
        draft.setIssueType(issueType != null && !issueType.isBlank() ? issueType.strip() : "Task");
        return jiraApiClient.diagnose(draft);
    }

    private static String normalizeBaseUrl(String url) {
        return url == null ? "" : url.strip().replaceAll("/+$", "");
    }
}
