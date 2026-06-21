package com.caseflow.ai.client;

import com.caseflow.ai.client.dto.request.AiReplyDraftRequest;
import com.caseflow.ai.client.dto.request.AiSummaryRequest;
import com.caseflow.ai.client.dto.response.AiRawReplyDraftResponse;
import com.caseflow.ai.client.dto.response.AiRawSummaryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link CaseflowAiClient} covering two concerns:
 *
 * <ol>
 *   <li>Exception contract — structure and fields of {@link AiServiceUnavailableException}</li>
 *   <li>Transport contract — request headers, successful deserialization, and failure
 *       classification (content-type mismatch, malformed JSON, correlationId visibility)</li>
 * </ol>
 */
class CaseflowAiClientTest {

    // ── Exception contract ────────────────────────────────────────────────────

    @Test
    void exception_preservesOperation_and_httpStatus() {
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/tickets/1/summary", 503, "Service unavailable");

        assertThat(ex.getOperation()).isEqualTo("/api/ai/tickets/1/summary");
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getMessage()).isEqualTo("Service unavailable");
    }

    @Test
    void exception_withCause_preservesChain() {
        RuntimeException cause = new RuntimeException("Connection refused");
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/tickets/1/reply-draft", "Unreachable: " + cause.getMessage(), cause);

        assertThat(ex.getOperation()).isEqualTo("/api/ai/tickets/1/reply-draft");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getHttpStatus()).isEqualTo(0);
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
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/policy-guidance", 500, "Internal server error");

        assertThat(ex.getHttpStatus()).isGreaterThanOrEqualTo(500);
        assertThat(ex.getHttpStatus()).isLessThan(600);
    }

    @Test
    void exception_clientError_isNonRetryable() {
        AiServiceUnavailableException ex = new AiServiceUnavailableException(
                "/api/ai/tickets/1/summary", 400, "Bad request from CaseFlow to AI service");

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

    // ── Transport contract ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Transport — HTTP headers, deserialization, and error classification")
    class TransportTest {

        private static final String BASE = "http://ai-test";

        private static final String SUMMARY_JSON = """
                {"summary":"Issue summary.","warnings":[],"model":"gpt-4o",\
                "promptVersion":"v1","generatedAt":"2026-04-16T10:00:00Z","correlationId":"corr-1"}
                """;

        private static final String REPLY_DRAFT_JSON = """
                {"suggestedBody":"Dear customer, ...","tone":"professional","warnings":[],"model":"gpt-4o",\
                "promptVersion":"v1","generatedAt":"2026-04-16T10:00:00Z","correlationId":"corr-1"}
                """;

        private MockRestServiceServer mockServer;
        private CaseflowAiClient client;

        @BeforeEach
        void setUp() {
            RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
            mockServer = MockRestServiceServer.bindTo(builder).build();
            client = new CaseflowAiClient(builder.build(), new AiClientProperties());
        }

        @AfterEach
        void verifyExpectations() {
            mockServer.verify();
        }

        // ── requestSummary sends explicit JSON headers ─────────────────────

        @Test
        void requestSummary_sendsAcceptApplicationJson() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/1/summary"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(header("Content-Type", containsString(MediaType.APPLICATION_JSON_VALUE)))
                    .andRespond(withSuccess(SUMMARY_JSON, MediaType.APPLICATION_JSON));

            AiRawSummaryResponse result = client.requestSummary(summaryRequest("corr-1"), 1L);

            assertThat(result.summary()).isEqualTo("Issue summary.");
            assertThat(result.model()).isEqualTo("gpt-4o");
            assertThat(result.correlationId()).isEqualTo("corr-1");
        }

        // ── requestReplyDraft sends explicit JSON headers ──────────────────

        @Test
        void requestReplyDraft_sendsAcceptApplicationJson() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/1/reply-draft"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(header("Content-Type", containsString(MediaType.APPLICATION_JSON_VALUE)))
                    .andRespond(withSuccess(REPLY_DRAFT_JSON, MediaType.APPLICATION_JSON));

            AiRawReplyDraftResponse result = client.requestReplyDraft(replyDraftRequest("corr-1"), 1L);

            assertThat(result.suggestedBody()).isEqualTo("Dear customer, ...");
            assertThat(result.tone()).isEqualTo("professional");
            assertThat(result.correlationId()).isEqualTo("corr-1");
        }

        // ── Successful deserialization ─────────────────────────────────────

        @Test
        void requestSummary_validJsonResponse_deserializesCorrectly() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/42/summary"))
                    .andRespond(withSuccess(SUMMARY_JSON, MediaType.APPLICATION_JSON));

            AiRawSummaryResponse result = client.requestSummary(summaryRequest("c-42"), 42L);

            assertThat(result.summary()).isEqualTo("Issue summary.");
            assertThat(result.warnings()).isEmpty();
            assertThat(result.model()).isEqualTo("gpt-4o");
            assertThat(result.promptVersion()).isEqualTo("v1");
        }

        @Test
        void requestReplyDraft_validJsonResponse_deserializesCorrectly() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/42/reply-draft"))
                    .andRespond(withSuccess(REPLY_DRAFT_JSON, MediaType.APPLICATION_JSON));

            AiRawReplyDraftResponse result = client.requestReplyDraft(replyDraftRequest("c-42"), 42L);

            assertThat(result.suggestedBody()).isEqualTo("Dear customer, ...");
            assertThat(result.tone()).isEqualTo("professional");
            assertThat(result.warnings()).isEmpty();
        }

        // ── Unexpected content type ────────────────────────────────────────

        @Test
        void requestSummary_unexpectedContentType_mapsToAiServiceUnavailable() {
            // Simulates AI service returning application/octet-stream instead of JSON
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/1/summary"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_OCTET_STREAM));

            assertThatThrownBy(() -> client.requestSummary(summaryRequest("corr-bad"), 1L))
                    .isInstanceOf(AiServiceUnavailableException.class)
                    .hasMessageContaining("content type");
        }

        @Test
        void requestReplyDraft_unexpectedContentType_mapsToAiServiceUnavailable() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/1/reply-draft"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_OCTET_STREAM));

            assertThatThrownBy(() -> client.requestReplyDraft(replyDraftRequest("corr-bad"), 1L))
                    .isInstanceOf(AiServiceUnavailableException.class)
                    .hasMessageContaining("content type");
        }

        // ── Malformed JSON ─────────────────────────────────────────────────

        @Test
        void requestSummary_malformedJson_mapsToAiServiceUnavailable() {
            // AI service returned application/json but the body is not valid JSON
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/1/summary"))
                    .andRespond(withSuccess("{not-valid-json", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.requestSummary(summaryRequest("corr-bad"), 1L))
                    .isInstanceOf(AiServiceUnavailableException.class);
        }

        @Test
        void requestReplyDraft_malformedJson_mapsToAiServiceUnavailable() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/1/reply-draft"))
                    .andRespond(withSuccess("NOT_JSON", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.requestReplyDraft(replyDraftRequest("corr-bad"), 1L))
                    .isInstanceOf(AiServiceUnavailableException.class);
        }

        // ── correlationId remains visible in diagnostics ───────────────────

        @Test
        void requestSummary_onContentTypeMismatch_operationFieldIdentifiesPath() {
            // Verifies that the thrown exception carries the path so callers/logs
            // can trace which operation failed without inspecting the message text.
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/7/summary"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_OCTET_STREAM));

            AiServiceUnavailableException ex = catchThrowableOfType(
                    () -> client.requestSummary(summaryRequest("corr-trace"), 7L),
                    AiServiceUnavailableException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getOperation()).isEqualTo("/api/ai/tickets/7/summary");
        }

        @Test
        void requestReplyDraft_onContentTypeMismatch_operationFieldIdentifiesPath() {
            mockServer.expect(requestTo(BASE + "/api/ai/tickets/7/reply-draft"))
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_OCTET_STREAM));

            AiServiceUnavailableException ex = catchThrowableOfType(
                    () -> client.requestReplyDraft(replyDraftRequest("corr-trace"), 7L),
                    AiServiceUnavailableException.class);

            assertThat(ex).isNotNull();
            assertThat(ex.getOperation()).isEqualTo("/api/ai/tickets/7/reply-draft");
        }

        // ── Test fixtures ──────────────────────────────────────────────────

        private static AiSummaryRequest summaryRequest(String correlationId) {
            return new AiSummaryRequest(
                    correlationId, "Acme Corp", "OPEN", "MEDIUM", "OK",
                    List.of(), List.of(), List.of(), "en", "STANDARD");
        }

        private static AiReplyDraftRequest replyDraftRequest(String correlationId) {
            return new AiReplyDraftRequest(
                    correlationId, "Acme Corp", "en", "professional",
                    "OPEN", "MEDIUM", List.of(), List.of(), List.of(),
                    List.of(), List.of(), "RESOLUTION", null);
        }
    }
}
