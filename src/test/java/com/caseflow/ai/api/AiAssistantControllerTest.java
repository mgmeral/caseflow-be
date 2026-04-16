package com.caseflow.ai.api;

import com.caseflow.ai.api.dto.AiAssistMetadata;
import com.caseflow.ai.api.dto.AiPolicyGuidanceAssistResponse;
import com.caseflow.ai.api.dto.AiReplyDraftAssistResponse;
import com.caseflow.ai.api.dto.AiSimilarCasesAssistResponse;
import com.caseflow.ai.api.dto.AiSummaryAssistResponse;
import com.caseflow.ai.service.AiAssistService;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.security.TicketAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiAssistantController.class)
@Import(SecurityConfig.class)
class AiAssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AiAssistService aiAssistService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    @BeforeEach
    void permitTicket() {
        when(ticketAuth.canReadTicket(any(Authentication.class), eq(1L))).thenReturn(true);
    }

    // ── /ai-summary ───────────────────────────────────────────────────────────

    @Test
    void summary_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/tickets/1/ai-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void summary_returns403_whenCannotViewTicket() throws Exception {
        when(ticketAuth.canReadTicket(any(Authentication.class), eq(99L))).thenReturn(false);

        mockMvc.perform(post("/api/tickets/99/ai-summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void summary_returns200_withSummary() throws Exception {
        when(aiAssistService.summarize(1L)).thenReturn(new AiSummaryAssistResponse(
                1L, "This is a login issue.", AiAssistMetadata.of("gpt-4o", "v1",
                        Instant.now().toString(), "corr-abc")));

        mockMvc.perform(post("/api/tickets/1/ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(1))
                .andExpect(jsonPath("$.summary").value("This is a login issue."))
                .andExpect(jsonPath("$.metadata.available").value(true))
                .andExpect(jsonPath("$.metadata.model").value("gpt-4o"));
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void summary_returns200_withUnavailableResponse_whenAiDown() throws Exception {
        when(aiAssistService.summarize(1L)).thenReturn(
                AiSummaryAssistResponse.unavailable(1L, "corr-xyz", "AI service unavailable"));

        mockMvc.perform(post("/api/tickets/1/ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.metadata.available").value(false))
                .andExpect(jsonPath("$.metadata.unavailableReason").value("AI service unavailable"));
    }

    @Test
    @WithMockUser
    void summary_returns403_whenMissingPermission() throws Exception {
        // Authenticated but lacks PERM_AI_ASSIST
        mockMvc.perform(post("/api/tickets/1/ai-summary"))
                .andExpect(status().isForbidden());
    }

    // ── /ai-reply-draft ───────────────────────────────────────────────────────

    @Test
    void replyDraft_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/tickets/1/ai-reply-draft"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void replyDraft_returns200_withDraft() throws Exception {
        when(aiAssistService.replyDraft(eq(1L), isNull())).thenReturn(
                new AiReplyDraftAssistResponse(1L, "Dear customer, ...", "professional",
                        AiAssistMetadata.of("gpt-4o", "v1", Instant.now().toString(), "corr-def")));

        mockMvc.perform(post("/api/tickets/1/ai-reply-draft")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value("Dear customer, ..."))
                .andExpect(jsonPath("$.toneApplied").value("professional"))
                .andExpect(jsonPath("$.metadata.available").value(true));
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void replyDraft_returns200_withDraft_withToneHint() throws Exception {
        when(aiAssistService.replyDraft(eq(1L), eq("empathetic"))).thenReturn(
                new AiReplyDraftAssistResponse(1L, "We understand your frustration...", "empathetic",
                        AiAssistMetadata.of("gpt-4o", "v1", Instant.now().toString(), "corr-ghi")));

        mockMvc.perform(post("/api/tickets/1/ai-reply-draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toneHint\":\"empathetic\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value("We understand your frustration..."))
                .andExpect(jsonPath("$.toneApplied").value("empathetic"));
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void replyDraft_returns200_withUnavailableResponse_whenAiDown() throws Exception {
        when(aiAssistService.replyDraft(eq(1L), isNull())).thenReturn(
                AiReplyDraftAssistResponse.unavailable(1L, "corr-xyz", "AI service unavailable"));

        mockMvc.perform(post("/api/tickets/1/ai-reply-draft")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.available").value(false));
    }

    // ── /ai-similar-cases ────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void similarCases_returns200_withCases() throws Exception {
        when(aiAssistService.similarCases(1L)).thenReturn(
                new AiSimilarCasesAssistResponse(1L,
                        List.of(new AiSimilarCasesAssistResponse.SimilarCase(
                                "TKT-0000050", "Similar issue", 0.9f, "Fixed by reset", List.of())),
                        AiAssistMetadata.of("gpt-4o", "v1", Instant.now().toString(), "corr-jkl")));

        mockMvc.perform(post("/api/tickets/1/ai-similar-cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases[0].ticketNo").value("TKT-0000050"))
                .andExpect(jsonPath("$.cases[0].similarityScore").value(0.9));
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void similarCases_returns200_withEmptyList_whenAiDown() throws Exception {
        when(aiAssistService.similarCases(1L)).thenReturn(
                AiSimilarCasesAssistResponse.unavailable(1L, "corr-xyz", "AI service unavailable"));

        mockMvc.perform(post("/api/tickets/1/ai-similar-cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cases").isArray())
                .andExpect(jsonPath("$.cases").isEmpty())
                .andExpect(jsonPath("$.metadata.available").value(false));
    }

    // ── /ai-policy-guidance ───────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void policyGuidance_returns400_whenQuestionMissing() throws Exception {
        mockMvc.perform(post("/api/tickets/1/ai-policy-guidance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PERM_AI_ASSIST")
    void policyGuidance_returns200_withGuidance() throws Exception {
        when(aiAssistService.policyGuidance(eq(1L), eq("What is the refund policy?"))).thenReturn(
                new AiPolicyGuidanceAssistResponse(1L, "Refunds are available within 30 days.",
                        List.of(),
                        AiAssistMetadata.of("gpt-4o", "v1", Instant.now().toString(), "corr-mno")));

        mockMvc.perform(post("/api/tickets/1/ai-policy-guidance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the refund policy?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guidance").value("Refunds are available within 30 days."))
                .andExpect(jsonPath("$.metadata.available").value(true));
    }
}
