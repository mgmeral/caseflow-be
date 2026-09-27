package com.caseflow.ai.client;

import com.caseflow.ai.client.dto.request.AiPolicyGuidanceRequest;
import com.caseflow.ai.client.dto.request.AiReplyDraftRequest;
import com.caseflow.ai.client.dto.request.AiSimilarCasesRequest;
import com.caseflow.ai.client.dto.request.AiSummaryRequest;
import com.caseflow.ai.client.dto.response.AiRawPolicyGuidanceResponse;
import com.caseflow.ai.client.dto.response.AiRawReplyDraftResponse;
import com.caseflow.ai.client.dto.response.AiRawSimilarCasesResponse;
import com.caseflow.ai.client.dto.response.AiRawSummaryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contract test between {@link CaseflowAiClient} and {@code caseflow-ai-service}.
 *
 * <p>For each of the four AI-assist endpoints it verifies the downstream path, that the request
 * body is exactly the shared fixture under {@code ai-contract/requests/} (which the AI service's
 * {@code BeContractTest} proves it accepts), and that a response body shaped exactly like the AI
 * service's DTO deserializes into every field BE depends on.
 *
 * <p>Before AI-001 the similar-cases and policy-guidance DTOs and paths had drifted apart
 * completely, and every call silently degraded to {@code available:false}.
 */
class CaseflowAiClientContractTest {

    private static final String BASE = "http://ai-test";

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

    @Test
    void summary_matchesAiServiceContract() {
        expect("/api/ai/tickets/1/summary", "requests/summary-request.json", "summary-response.json");

        AiRawSummaryResponse res = client.requestSummary(new AiSummaryRequest(
                "corr-1", "Acme Corp", "IN_PROGRESS", "MEDIUM", "OK", List.of("AUTH"),
                List.of(new AiSummaryRequest.LatestMessage(
                        "inbound", "c***@acme.com", "I cannot log in since yesterday.", "2026-09-27T09:00:00Z")),
                List.of("Checked the account, it is not locked."), "en", "STANDARD"), 1L);

        assertThat(res.summary()).isEqualTo("Customer cannot log in after a password change.");
        assertThat(res.model()).isEqualTo("llama3.1");
        assertThat(res.promptVersion()).isEqualTo("1.0");
        assertThat(res.generatedAt()).isEqualTo("2026-09-27T10:00:00Z");
        assertThat(res.warnings()).isEmpty();
    }

    @Test
    void replyDraft_matchesAiServiceContract() {
        expect("/api/ai/tickets/1/reply-draft", "requests/reply-draft-request.json", "reply-draft-response.json");

        AiRawReplyDraftResponse res = client.requestReplyDraft(new AiReplyDraftRequest(
                "corr-1", "Acme Corp", "en", "professional", "IN_PROGRESS", "MEDIUM", List.of("AUTH"),
                List.of(new AiReplyDraftRequest.LatestMessage(
                        "inbound", "c***@acme.com", "I cannot log in since yesterday.", "2026-09-27T09:00:00Z")),
                List.of("Checked the account, it is not locked."), List.of(),
                List.of("SLA resolution deadline in 120 minutes"), "RESOLUTION", "CUSTOMER_REPLY"), 1L);

        assertThat(res.suggestedBody()).isEqualTo("Dear customer, we have reset your password.");
        assertThat(res.tone()).isEqualTo("PROFESSIONAL");
        assertThat(res.model()).isEqualTo("llama3.1");
    }

    @Test
    void similarCases_matchesAiServiceContract() {
        expect("/api/ai/tickets/1/similar-cases", "requests/similar-cases-request.json",
                "similar-cases-response.json");

        AiRawSimilarCasesResponse res = client.requestSimilarCases(new AiSimilarCasesRequest(
                "corr-1", "Login issue\nUser cannot log in after a password change.",
                "Acme Corp", List.of("AUTH"), 15), 1L);

        assertThat(res.matches()).singleElement().satisfies(m -> {
            assertThat(m.sourceId()).isEqualTo("t-50");
            assertThat(m.title()).isEqualTo("Similar login issue");
            assertThat(m.snippet()).startsWith("User could not log in");
            assertThat(m.score()).isEqualTo(0.91);
            assertThat(m.metadata()).containsEntry("tags", "AUTH,LOGIN");
        });
        assertThat(res.model()).isEqualTo("llama3.1");
        assertThat(res.warnings()).isEmpty();
    }

    @Test
    void policyGuidance_matchesAiServiceContract() {
        expect("/api/ai/tickets/1/policy-guidance", "requests/policy-guidance-request.json",
                "policy-guidance-response.json");

        AiRawPolicyGuidanceResponse res = client.requestPolicyGuidance(policyRequest(), 1L);

        assertThat(res.answer()).isEqualTo("Refunds are available within 30 days of purchase.");
        assertThat(res.recommendedActions()).containsExactly("Confirm the purchase date", "Offer a refund");
        assertThat(res.confidence()).isEqualTo(0.74);
        assertThat(res.policyReferences()).singleElement().satisfies(r -> {
            assertThat(r.sourceId()).isEqualTo("pol-7");
            assertThat(r.title()).isEqualTo("Refund policy");
            assertThat(r.snippet()).startsWith("Customers may request a refund");
            assertThat(r.score()).isEqualTo(0.81);
        });
    }

    @Test
    void policyGuidance_noPolicyFound_deserializesAsEmptyReferencesWithWarning() {
        expect("/api/ai/tickets/1/policy-guidance", "requests/policy-guidance-request.json",
                "policy-guidance-no-policy-response.json");

        AiRawPolicyGuidanceResponse res = client.requestPolicyGuidance(policyRequest(), 1L);

        assertThat(res.policyReferences()).isEmpty();
        assertThat(res.confidence()).isZero();
        assertThat(res.warnings()).hasSize(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AiPolicyGuidanceRequest policyRequest() {
        return new AiPolicyGuidanceRequest("corr-1", "What is the refund policy?", "Acme Corp",
                "IN_PROGRESS", "MEDIUM", List.of("BILLING"), 5);
    }

    private void expect(String path, String requestFixture, String responseFixture) {
        mockServer.expect(requestTo(BASE + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(fixture(requestFixture), true))
                .andRespond(withSuccess(fixture(responseFixture), MediaType.APPLICATION_JSON));
    }

    private static String fixture(String name) {
        try {
            return new ClassPathResource("ai-contract/" + name).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
