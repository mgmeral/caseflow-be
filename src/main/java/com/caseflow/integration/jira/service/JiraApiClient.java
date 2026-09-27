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

import java.util.ArrayList;
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

    /**
     * Checks, in order, everything issue creation depends on: the credentials, the project and the
     * issue type. Stops at the first failure (later checks are reported as skipped) and never throws.
     * Messages are written for the admin fixing the form, not for logs.
     */
    public JiraDiagnostics diagnose(JiraConfig config) {
        String base = config.getBaseUrl().replaceAll("/+$", "");
        HttpEntity<Void> request = new HttpEntity<>(buildHeaders(config));
        List<JiraCheck> checks = new ArrayList<>();

        // 1. Credentials
        try {
            Map<?, ?> me = restTemplate.exchange(base + "/rest/api/3/myself", HttpMethod.GET, request, Map.class).getBody();
            String who = me != null && me.get("displayName") != null ? String.valueOf(me.get("displayName")) : config.getUsername();
            checks.add(new JiraCheck(CHECK_AUTH, true, "Signed in to Jira as " + who + "."));
        } catch (HttpClientErrorException e) {
            checks.add(new JiraCheck(CHECK_AUTH, false, switch (e.getStatusCode().value()) {
                case 401 -> "Jira rejected the e-mail / API token (401). Check both, or create a new token.";
                case 403 -> "This Jira account is not allowed to use the REST API (403).";
                case 404 -> "No Jira Cloud API at this address (404). Use your site root, e.g. https://company.atlassian.net.";
                default -> "Jira answered " + e.getStatusCode().value() + " to the sign-in check.";
            }));
            return skipRest(checks);
        } catch (ResourceAccessException e) {
            checks.add(new JiraCheck(CHECK_AUTH, false, "Cannot reach " + base + ". Check the address and that it is reachable from the CaseFlow server."));
            return skipRest(checks);
        } catch (Exception e) {
            checks.add(new JiraCheck(CHECK_AUTH, false, "Unexpected answer from " + base + " — is this a Jira Cloud site?"));
            return skipRest(checks);
        }

        // 2. Project
        String key = config.getProjectKey();
        try {
            Map<?, ?> project = restTemplate.exchange(base + "/rest/api/3/project/" + key, HttpMethod.GET, request, Map.class).getBody();
            String name = project != null && project.get("name") != null ? " (" + project.get("name") + ")" : "";
            checks.add(new JiraCheck(CHECK_PROJECT, true, "Project " + key + name + " found."));
        } catch (HttpClientErrorException e) {
            checks.add(new JiraCheck(CHECK_PROJECT, false, e.getStatusCode().value() == 404
                    ? "Project " + key + " does not exist, or this Jira account cannot see it."
                    : "Jira answered " + e.getStatusCode().value() + " when reading project " + key + "."));
            return skipRest(checks);
        } catch (Exception e) {
            checks.add(new JiraCheck(CHECK_PROJECT, false, "Could not read project " + key + "."));
            return skipRest(checks);
        }

        // 3. Issue type — this endpoint only lists types the account may create in the project.
        List<String> issueTypes = new ArrayList<>();
        try {
            Map<?, ?> meta = restTemplate.exchange(base + "/rest/api/3/issue/createmeta/" + key + "/issuetypes",
                    HttpMethod.GET, request, Map.class).getBody();
            Object list = meta == null ? null : meta.get("issueTypes") != null ? meta.get("issueTypes") : meta.get("values");
            if (list instanceof List<?> types) {
                for (Object t : types) {
                    if (t instanceof Map<?, ?> m && m.get("name") != null) issueTypes.add(String.valueOf(m.get("name")));
                }
            }
        } catch (Exception e) {
            checks.add(new JiraCheck(CHECK_ISSUE_TYPE, false, "Could not list the issue types of project " + key + "."));
            return new JiraDiagnostics(false, checks, issueTypes);
        }
        String wanted = config.getIssueType();
        if (issueTypes.isEmpty()) {
            checks.add(new JiraCheck(CHECK_ISSUE_TYPE, false,
                    "This Jira account cannot create issues in " + key + ". Give it the \"Create issues\" project permission."));
        } else if (issueTypes.stream().anyMatch(t -> t.equalsIgnoreCase(wanted))) {
            checks.add(new JiraCheck(CHECK_ISSUE_TYPE, true, "Issue type \"" + wanted + "\" can be created in " + key + "."));
        } else {
            checks.add(new JiraCheck(CHECK_ISSUE_TYPE, false,
                    "Issue type \"" + wanted + "\" is not available in " + key + ". Available: " + String.join(", ", issueTypes) + "."));
        }
        return new JiraDiagnostics(checks.stream().allMatch(JiraCheck::ok), checks, issueTypes);
    }

    private static JiraDiagnostics skipRest(List<JiraCheck> checks) {
        for (String key : List.of(CHECK_AUTH, CHECK_PROJECT, CHECK_ISSUE_TYPE)) {
            if (checks.stream().noneMatch(c -> c.key().equals(key))) {
                checks.add(new JiraCheck(key, false, "Skipped — fix the step above first."));
            }
        }
        return new JiraDiagnostics(false, checks, List.of());
    }

    public static final String CHECK_AUTH = "AUTH";
    public static final String CHECK_PROJECT = "PROJECT";
    public static final String CHECK_ISSUE_TYPE = "ISSUE_TYPE";

    /** One step of {@link #diagnose}; {@code key} is AUTH, PROJECT or ISSUE_TYPE. */
    public record JiraCheck(String key, boolean ok, String message) {}

    /** Result of {@link #diagnose}; {@code issueTypes} lists what the account may create (empty unless reached). */
    public record JiraDiagnostics(boolean success, List<JiraCheck> checks, List<String> issueTypes) {}

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
