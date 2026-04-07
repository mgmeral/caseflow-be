package com.caseflow.integration.jira.service;

import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Low-level Jira REST API v3 client.
 *
 * <p>All methods throw {@link IntegrationJobExecutionException} with
 * {@code permanent=true} for configuration errors (401, 403, 404 on project)
 * and {@code permanent=false} for transient failures (5xx, network errors).
 */
@Component
public class JiraApiClient {

    private static final Logger log = LoggerFactory.getLogger(JiraApiClient.class);

    private final RestTemplate restTemplate;

    public JiraApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Creates a Jira issue and returns the result containing the key, id, and URL.
     */
    public JiraIssueResult createIssue(JiraConfig config, JiraIssuePayload payload) {
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/rest/api/3/issue";

        String body = buildCreateIssueBody(config, payload);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders(config)),
                    Map.class
            );

            Map<?, ?> body2 = response.getBody();
            if (body2 == null) {
                throw new IntegrationJobExecutionException("Jira returned empty response body", false);
            }

            String issueKey = (String) body2.get("key");
            String issueId = String.valueOf(body2.get("id"));
            String issueUrl = config.getBaseUrl().replaceAll("/+$", "") + "/browse/" + issueKey;

            log.info("Jira issue created: {} ({})", issueKey, issueId);
            return new JiraIssueResult(issueKey, issueId, issueUrl);

        } catch (HttpClientErrorException e) {
            boolean permanent = e.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || e.getStatusCode() == HttpStatus.FORBIDDEN
                    || e.getStatusCode() == HttpStatus.NOT_FOUND;
            throw new IntegrationJobExecutionException(
                    "Jira API error " + e.getStatusCode() + ": " + truncate(e.getResponseBodyAsString(), 500),
                    permanent, e);
        } catch (HttpServerErrorException e) {
            throw new IntegrationJobExecutionException(
                    "Jira server error " + e.getStatusCode(), false, e);
        } catch (ResourceAccessException e) {
            throw new IntegrationJobExecutionException(
                    "Cannot reach Jira: " + e.getMessage(), false, e);
        }
    }

    /**
     * Tests Jira connectivity by calling the myself endpoint.
     *
     * @return true if the credentials are valid
     * @throws IntegrationJobExecutionException on auth or network failure
     */
    public boolean testConnection(JiraConfig config) {
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/rest/api/3/myself";
        try {
            restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(config)), Map.class);
            return true;
        } catch (HttpClientErrorException e) {
            throw new IntegrationJobExecutionException(
                    "Jira auth test failed: " + e.getStatusCode(), true, e);
        } catch (Exception e) {
            throw new IntegrationJobExecutionException(
                    "Jira connection test failed: " + e.getMessage(), false, e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private HttpHeaders buildHeaders(JiraConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        if ("BASIC".equalsIgnoreCase(config.getAuthType())
                && config.getUsername() != null && config.getApiToken() != null) {
            String creds = config.getUsername() + ":" + config.getApiToken();
            headers.set("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(creds.getBytes()));
        }
        return headers;
    }

    private String buildCreateIssueBody(JiraConfig config, JiraIssuePayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"fields\":{");
        sb.append("\"project\":{\"key\":\"").append(escapeJson(config.getProjectKey())).append("\"},");
        sb.append("\"issuetype\":{\"name\":\"").append(escapeJson(config.getIssueType())).append("\"},");
        sb.append("\"summary\":\"").append(escapeJson(payload.summary())).append("\",");

        // ADF (Atlassian Document Format) for description
        sb.append("\"description\":{\"type\":\"doc\",\"version\":1,\"content\":[");
        sb.append("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"");
        sb.append(escapeJson(payload.description()));
        sb.append("\"}]}]}");

        if (config.getDefaultLabels() != null && !config.getDefaultLabels().isBlank()) {
            sb.append(",\"labels\":[");
            String[] labels = config.getDefaultLabels().split(",");
            for (int i = 0; i < labels.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(labels[i].trim())).append("\"");
            }
            sb.append("]");
        }

        sb.append("}}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** Immutable result from a successful Jira issue creation. */
    public record JiraIssueResult(String issueKey, String issueId, String issueUrl) {}

    /** Input payload for issue creation. */
    public record JiraIssuePayload(String summary, String description) {}
}
