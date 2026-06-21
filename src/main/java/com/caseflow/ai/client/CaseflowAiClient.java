package com.caseflow.ai.client;

import com.caseflow.ai.client.dto.request.AiPolicyGuidanceRequest;
import com.caseflow.ai.client.dto.request.AiReplyDraftRequest;
import com.caseflow.ai.client.dto.request.AiSimilarCasesRequest;
import com.caseflow.ai.client.dto.request.AiSummaryRequest;
import com.caseflow.ai.client.dto.response.AiRawPolicyGuidanceResponse;
import com.caseflow.ai.client.dto.response.AiRawReplyDraftResponse;
import com.caseflow.ai.client.dto.response.AiRawSimilarCasesResponse;
import com.caseflow.ai.client.dto.response.AiRawSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;

/**
 * HTTP client to the {@code caseflow-ai-service}.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Owns the RestClient instance with pre-configured timeouts</li>
 *   <li>Explicitly requests {@code application/json} on every outbound call — not relying on defaults</li>
 *   <li>Propagates {@code X-Correlation-ID} on every request</li>
 *   <li>Maps HTTP errors to {@link AiServiceUnavailableException}</li>
 *   <li>Maps content-type mismatches and JSON parse failures to {@link AiServiceUnavailableException}
 *       with structured diagnostics (path, correlationId, contentType, target DTO)</li>
 *   <li>Retries on transient network failures and 5xx responses</li>
 * </ul>
 *
 * <h2>What it does NOT do</h2>
 * Does NOT apply business logic, fallback, caching, or authorization.
 * Those responsibilities belong to {@link com.caseflow.ai.service.AiAssistService}.
 */
@Component
public class CaseflowAiClient {

    private static final Logger log = LoggerFactory.getLogger(CaseflowAiClient.class);
    private static final int MESSAGE_SNIPPET_LIMIT = 200;

    private final RestClient restClient;
    private final AiClientProperties properties;

    public CaseflowAiClient(@Qualifier("aiRestClient") RestClient restClient,
                            AiClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    // ── Public operations ─────────────────────────────────────────────────────

    /**
     * Calls the AI service's {@code /api/ai/tickets/{ticketId}/summary} endpoint.
     *
     * @param ticketId the CaseFlow ticket id, embedded in the downstream path
     * @throws AiServiceUnavailableException on any network, HTTP, or deserialization error
     */
    @Retryable(
            retryFor = {AiServiceUnavailableException.class},
            maxAttemptsExpression = "#{@aiClientProperties.retry.maxAttempts}",
            backoff = @Backoff(delayExpression = "#{@aiClientProperties.retry.backoffMs}")
    )
    public AiRawSummaryResponse requestSummary(AiSummaryRequest request, Long ticketId) {
        String path = "/api/ai/tickets/" + ticketId + "/summary";
        log.debug("AI summary request — correlationId={}, ticketId={}", request.correlationId(), ticketId);
        return post(path, request, request.correlationId(), AiRawSummaryResponse.class);
    }

    /**
     * Calls the AI service's {@code /api/ai/tickets/{ticketId}/reply-draft} endpoint.
     *
     * @param ticketId the CaseFlow ticket id, embedded in the downstream path
     * @throws AiServiceUnavailableException on any network, HTTP, or deserialization error
     */
    @Retryable(
            retryFor = {AiServiceUnavailableException.class},
            maxAttemptsExpression = "#{@aiClientProperties.retry.maxAttempts}",
            backoff = @Backoff(delayExpression = "#{@aiClientProperties.retry.backoffMs}")
    )
    public AiRawReplyDraftResponse requestReplyDraft(AiReplyDraftRequest request, Long ticketId) {
        String path = "/api/ai/tickets/" + ticketId + "/reply-draft";
        log.debug("AI reply-draft request — correlationId={}, ticketId={}", request.correlationId(), ticketId);
        return post(path, request, request.correlationId(), AiRawReplyDraftResponse.class);
    }

    /**
     * Calls the AI service's {@code /api/ai/similar-cases} endpoint.
     *
     * @throws AiServiceUnavailableException on any network, HTTP, or deserialization error
     */
    @Retryable(
            retryFor = {AiServiceUnavailableException.class},
            maxAttemptsExpression = "#{@aiClientProperties.retry.maxAttempts}",
            backoff = @Backoff(delayExpression = "#{@aiClientProperties.retry.backoffMs}")
    )
    public AiRawSimilarCasesResponse requestSimilarCases(AiSimilarCasesRequest request) {
        log.debug("AI similar-cases request — correlationId={}, ticketNo={}",
                request.correlationId(), request.ticketNo());
        return post("/api/ai/similar-cases", request, request.correlationId(), AiRawSimilarCasesResponse.class);
    }

    /**
     * Calls the AI service's {@code /api/ai/policy-guidance} endpoint.
     *
     * @throws AiServiceUnavailableException on any network, HTTP, or deserialization error
     */
    @Retryable(
            retryFor = {AiServiceUnavailableException.class},
            maxAttemptsExpression = "#{@aiClientProperties.retry.maxAttempts}",
            backoff = @Backoff(delayExpression = "#{@aiClientProperties.retry.backoffMs}")
    )
    public AiRawPolicyGuidanceResponse requestPolicyGuidance(AiPolicyGuidanceRequest request) {
        log.debug("AI policy-guidance request — correlationId={}, ticketNo={}",
                request.correlationId(), request.ticketNo());
        return post("/api/ai/policy-guidance", request, request.correlationId(), AiRawPolicyGuidanceResponse.class);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Executes a POST request, explicitly sets Content-Type and Accept to application/json,
     * and maps every failure mode to {@link AiServiceUnavailableException} with structured
     * diagnostics so the log unambiguously identifies whether the failure was:
     * <ul>
     *   <li>a content-type mismatch ({@link UnknownContentTypeException})</li>
     *   <li>a JSON parse / mapping failure ({@link HttpMessageNotReadableException})</li>
     *   <li>a network timeout or unreachable host ({@link ResourceAccessException})</li>
     *   <li>an HTTP 4xx/5xx status ({@link RestClientResponseException})</li>
     *   <li>any other RestClient issue ({@link RestClientException})</li>
     * </ul>
     */
    private <T> T post(String path, Object body, String correlationId, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "")
                    .header("X-Source", "caseflow-be")
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        int code = res.getStatusCode().value();
                        throw new AiServiceUnavailableException(path, code,
                                "AI service returned client error " + code + " for " + path);
                    })
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        int code = res.getStatusCode().value();
                        throw new AiServiceUnavailableException(path, code,
                                "AI service returned server error " + code + " for " + path);
                    })
                    .body(responseType);

        } catch (AiServiceUnavailableException ex) {
            int status = ex.getHttpStatus();
            if (status >= 400 && status < 500) {
                log.warn("AI service client error (non-retryable) [correlationId={}, path={}, status={}]: {}",
                        correlationId, path, status, ex.getMessage());
                throw new AiServiceUnavailableException(path,
                        "AI service client error (non-retryable): " + ex.getMessage(), ex);
            }
            log.warn("AI service server error [correlationId={}, path={}, status={}]: {}",
                    correlationId, path, status, ex.getMessage());
            throw ex;

        } catch (UnknownContentTypeException ex) {
            // AI service responded with a content type the JSON converter cannot handle
            // (e.g. application/octet-stream). We are fixing the AI service separately;
            // the backend must not crash — convert and log clearly.
            log.warn("AI service content-type mismatch [correlationId={}, path={}, contentType={}, target={}]: {}",
                    correlationId, path, ex.getContentType(), responseType.getSimpleName(),
                    snippet(ex.getMessage()));
            throw new AiServiceUnavailableException(path,
                    "AI service response has unexpected content type: " + ex.getContentType(), ex);

        } catch (HttpMessageNotReadableException ex) {
            // AI service returned application/json but the body was not valid JSON
            // or did not match the expected structure.
            log.warn("AI service malformed JSON [correlationId={}, path={}, target={}]: {}",
                    correlationId, path, responseType.getSimpleName(), snippet(ex.getMessage()));
            throw new AiServiceUnavailableException(path,
                    "AI service response deserialization failed: " + snippet(ex.getMessage()), ex);

        } catch (ResourceAccessException ex) {
            log.warn("AI service unreachable [correlationId={}, path={}]: {}",
                    correlationId, path, ex.getMessage());
            throw new AiServiceUnavailableException(path,
                    "AI service unreachable: " + ex.getMessage(), ex);

        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            log.warn("AI service HTTP error [correlationId={}, path={}, status={}]",
                    correlationId, path, code);
            throw new AiServiceUnavailableException(path, code,
                    "AI service HTTP error " + code);

        } catch (RestClientException ex) {
            // Safety net: any RestClient issue not covered by the specific catches above
            // (e.g. a converter error whose cause is not HttpMessageNotReadableException).
            log.warn("AI service response error [correlationId={}, path={}, target={}]: {}",
                    correlationId, path, responseType.getSimpleName(), snippet(ex.getMessage()));
            throw new AiServiceUnavailableException(path,
                    "AI service response error: " + snippet(ex.getMessage()), ex);

        } catch (Exception ex) {
            log.warn("AI service unexpected error [correlationId={}, path={}]: {}",
                    correlationId, path, ex.getMessage());
            throw new AiServiceUnavailableException(path,
                    "AI service unexpected error: " + ex.getMessage(), ex);
        }
    }

    /** Caps exception messages before they reach the log to avoid writing large bodies. */
    private static String snippet(String message) {
        if (message == null) return null;
        return message.length() > MESSAGE_SNIPPET_LIMIT
                ? message.substring(0, MESSAGE_SNIPPET_LIMIT) + "…"
                : message;
    }
}
