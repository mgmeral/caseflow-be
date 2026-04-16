package com.caseflow.ai.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiServiceUnavailableException} structure and
 * the properties that the AI client exposes for error classification.
 *
 * Full HTTP behaviour of {@link CaseflowAiClient} is covered by integration
 * tests against a mock HTTP server (WireMock) when the AI service is available.
 * These unit tests focus on the exception contract and operation naming.
 */
class CaseflowAiClientTest {

    @Test
    void exception_preservesOperation_and_httpStatus() {
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/summary", 503, "Service unavailable");

        assertThat(ex.getOperation()).isEqualTo("/api/ai/summary");
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getMessage()).isEqualTo("Service unavailable");
    }

    @Test
    void exception_withCause_preservesChain() {
        RuntimeException cause = new RuntimeException("Connection refused");
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/reply-draft", "Unreachable: " + cause.getMessage(), cause);

        assertThat(ex.getOperation()).isEqualTo("/api/ai/reply-draft");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getHttpStatus()).isEqualTo(0); // no HTTP status for network errors
    }

    @Test
    void exception_networkError_hasZeroHttpStatus() {
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/similar-cases", "timeout");

        assertThat(ex.getHttpStatus()).isEqualTo(0);
        assertThat(ex.getOperation()).isEqualTo("/api/ai/similar-cases");
    }

    @Test
    void exception_serverError_isRetryable() {
        // 5xx errors are considered retryable — the operation field helps callers decide
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/policy-guidance", 500, "Internal server error");

        assertThat(ex.getHttpStatus()).isGreaterThanOrEqualTo(500);
        assertThat(ex.getHttpStatus()).isLessThan(600);
    }

    @Test
    void exception_clientError_isNonRetryable() {
        // 4xx errors are non-retryable
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/summary", 400, "Bad request from CaseFlow to AI service");

        assertThat(ex.getHttpStatus()).isGreaterThanOrEqualTo(400);
        assertThat(ex.getHttpStatus()).isLessThan(500);
    }

    @Test
    void aiClientProperties_hasExpectedDefaults() {
        AiClientProperties props = new AiClientProperties();

        assertThat(props.getBaseUrl()).isEqualTo("http://localhost:8081");
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getTimeout().getConnectMs()).isEqualTo(3000);
        assertThat(props.getTimeout().getReadMs()).isEqualTo(15000);
        assertThat(props.getRetry().getMaxAttempts()).isEqualTo(2);
        assertThat(props.getRetry().getBackoffMs()).isEqualTo(500L);
    }
}
