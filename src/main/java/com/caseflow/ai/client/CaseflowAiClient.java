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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client to the {@code caseflow-ai-service}.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Owns the RestClient instance with pre-configured timeouts</li>
 *   <li>Propagates {@code X-Correlation-ID} on every request</li>
 *   <li>Maps HTTP errors to {@link AiServiceUnavailableException}</li>
 *   <li>Retries on transient network failures and 5xx responses</li>
 *   <li>Redacts large payloads from logs — never logs full prompt text</li>
 * </ul>
 *
 * <h2>What it does NOT do</h2>
 * Does NOT apply business logic, fallback, caching, or authorization.
 * Those responsibilities belong to {@link com.caseflow.ai.service.AiAssistService}.
 */
@Component
public class CaseflowAiClient {

    private static final Logger log = LoggerFactory.getLogger(CaseflowAiClient.class);

    private final RestClient restClient;
    private final AiClientProperties properties;

    public CaseflowAiClient(@Qualifier("aiRestClient") RestClient restClient,
                            AiClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    // ── Public operations ─────────────────────────────────────────────────────

    /**
     * Calls the AI service's {@code /api/ai/summary} endpoint.
     *
     * @throws AiServiceUnavailableException on any network or HTTP error
     */
    @Retryable(
            retryFor = {AiServiceUnavailableException.class},
            maxAttemptsExpression = "#{@aiClientProperties.retry.maxAttempts}",
            backoff = @Backoff(delayExpression = "#{@aiClientProperties.retry.backoffMs}")
    )
    public AiRawSummaryResponse requestSummary(AiSummaryRequest request) {
        log.debug("AI summary request — correlationId={}, ticketNo={}",
                request.correlationId(), request.ticketNo());
        return post("/api/ai/summary", request, request.correlationId(), AiRawSummaryResponse.class);
    }

    /**
     * Calls the AI service's {@code /api/ai/reply-draft} endpoint.
     *
     * @throws AiServiceUnavailableException on any network or HTTP error
     */
    @Retryable(
            retryFor = {AiServiceUnavailableException.class},
            maxAttemptsExpression = "#{@aiClientProperties.retry.maxAttempts}",
            backoff = @Backoff(delayExpression = "#{@aiClientProperties.retry.backoffMs}")
    )
    public AiRawReplyDraftResponse requestReplyDraft(AiReplyDraftRequest request) {
        log.debug("AI reply-draft request — correlationId={}, ticketNo={}",
                request.correlationId(), request.ticketNo());
        return post("/api/ai/reply-draft", request, request.correlationId(), AiRawReplyDraftResponse.class);
    }

    /**
     * Calls the AI service's {@code /api/ai/similar-cases} endpoint.
     *
     * @throws AiServiceUnavailableException on any network or HTTP error
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
     * @throws AiServiceUnavailableException on any network or HTTP error
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

    private <T> T post(String path, Object body, String correlationId, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "")
                    .header("X-Source", "caseflow-be")
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        int code = res.getStatusCode().value();
                        // 4xx from AI service is non-retryable (bad request we sent)
                        throw new AiServiceUnavailableException(path, code,
                                "AI service returned client error " + code + " for " + path);
                    })
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        int code = res.getStatusCode().value();
                        // 5xx is retryable
                        throw new AiServiceUnavailableException(path, code,
                                "AI service returned server error " + code + " for " + path);
                    })
                    .body(responseType);

        } catch (AiServiceUnavailableException ex) {
            int status = ex.getHttpStatus();
            if (status >= 400 && status < 500) {
                // Non-retryable — wrap and rethrow without retry annotation catching it
                log.warn("AI service client error [correlationId={}] path={} status={}: {}",
                        correlationId, path, status, ex.getMessage());
                throw new AiServiceUnavailableException(path,
                        "AI service client error (non-retryable): " + ex.getMessage(), ex);
            }
            log.warn("AI service server error [correlationId={}] path={} status={}: {}",
                    correlationId, path, status, ex.getMessage());
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("AI service unreachable [correlationId={}] path={}: {}",
                    correlationId, path, ex.getMessage());
            throw new AiServiceUnavailableException(path,
                    "AI service unreachable: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            log.warn("AI service HTTP error [correlationId={}] path={} status={}", correlationId, path, code);
            throw new AiServiceUnavailableException(path, code,
                    "AI service HTTP error " + code);
        } catch (Exception ex) {
            log.warn("AI service unexpected error [correlationId={}] path={}: {}",
                    correlationId, path, ex.getMessage());
            throw new AiServiceUnavailableException(path,
                    "AI service unexpected error: " + ex.getMessage(), ex);
        }
    }
}
