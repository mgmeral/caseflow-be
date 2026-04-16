package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.AiPolicyGuidanceAssistResponse;
import com.caseflow.ai.api.dto.AiReplyDraftAssistResponse;
import com.caseflow.ai.api.dto.AiSimilarCasesAssistResponse;
import com.caseflow.ai.api.dto.AiSummaryAssistResponse;
import com.caseflow.ai.client.AiServiceUnavailableException;
import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.request.AiSummaryRequest;
import com.caseflow.ai.client.dto.response.AiRawReplyDraftResponse;
import com.caseflow.ai.client.dto.response.AiRawSimilarCasesResponse;
import com.caseflow.ai.client.dto.response.AiRawSummaryResponse;
import com.caseflow.ai.context.TicketAiContextBuilder;
import com.caseflow.ai.context.dto.PolicyGuidanceContext;
import com.caseflow.ai.context.dto.ReplyDraftContext;
import com.caseflow.ai.context.dto.SimilarCasesContext;
import com.caseflow.ai.context.dto.SummaryContext;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistServiceTest {

    @Mock private CaseflowAiClient aiClient;
    @Mock private TicketAiContextBuilder contextBuilder;
    @Mock private AiAvailabilityService availabilityService;
    @Mock private TicketRepository ticketRepository;

    @InjectMocks
    private AiAssistService sut;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setSubject("Login issue");
        setId(ticket, 1L);
        setTicketNo(ticket, "TKT-0000001");
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Test
    void summarize_throwsNotFound_whenTicketMissing() {
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.summarize(99L))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void summarize_returnsUnavailable_whenAiDisabled() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(false);

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isNull();
        assertThat(result.metadata().available()).isFalse();
        assertThat(result.metadata().unavailableReason()).isEqualTo("AI service disabled");
        verify(aiClient, never()).requestSummary(any());
    }

    @Test
    void summarize_returnsUnavailable_whenAiClientThrows() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any()))
                .thenThrow(new AiServiceUnavailableException("/api/ai/summary", "timeout"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isNull();
        assertThat(result.metadata().available()).isFalse();
        assertThat(result.metadata().unavailableReason()).isEqualTo("AI service unavailable");
    }

    @Test
    void summarize_returnsUnavailable_onUnexpectedError() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any())).thenThrow(new RuntimeException("unexpected"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isNull();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void summarize_returnsResponse_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any())).thenReturn(
                new AiRawSummaryResponse("Issue summary here.", "gpt-4o", "v1",
                        Instant.now().toString(), "corr-123"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isEqualTo("Issue summary here.");
        assertThat(result.metadata().available()).isTrue();
        assertThat(result.metadata().model()).isEqualTo("gpt-4o");
        assertThat(result.ticketId()).isEqualTo(1L);
    }

    // ── Reply draft ───────────────────────────────────────────────────────────

    @Test
    void replyDraft_returnsUnavailable_whenAiDisabled() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(false);

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, null);

        assertThat(result.draft()).isNull();
        assertThat(result.metadata().available()).isFalse();
        verify(aiClient, never()).requestReplyDraft(any());
    }

    @Test
    void replyDraft_returnsUnavailable_whenAiClientThrows() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any()))
                .thenThrow(new AiServiceUnavailableException("/api/ai/reply-draft", 503, "service unavailable"));

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, null);

        assertThat(result.draft()).isNull();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void replyDraft_returnsResponse_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any())).thenReturn(
                new AiRawReplyDraftResponse("Dear customer, ...", "professional",
                        "gpt-4o", "v1", Instant.now().toString(), "corr-456"));

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, "empathetic");

        assertThat(result.draft()).isEqualTo("Dear customer, ...");
        assertThat(result.toneApplied()).isEqualTo("professional");
        assertThat(result.metadata().available()).isTrue();
    }

    // ── Similar cases ─────────────────────────────────────────────────────────

    @Test
    void similarCases_returnsEmptyList_whenAiDisabled() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(false);

        AiSimilarCasesAssistResponse result = sut.similarCases(1L);

        assertThat(result.cases()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void similarCases_returnsEmptyList_whenAiClientThrows() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSimilarCasesContext(ticket)).thenReturn(stubSimilarCasesContext());
        when(aiClient.requestSimilarCases(any()))
                .thenThrow(new AiServiceUnavailableException("/api/ai/similar-cases", "timeout"));

        AiSimilarCasesAssistResponse result = sut.similarCases(1L);

        assertThat(result.cases()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void similarCases_returnsCases_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSimilarCasesContext(ticket)).thenReturn(stubSimilarCasesContext());
        when(aiClient.requestSimilarCases(any())).thenReturn(
                new AiRawSimilarCasesResponse(
                        List.of(new AiRawSimilarCasesResponse.SimilarCase(
                                "TKT-0000050", "Similar login issue", 0.92f,
                                "Reset password resolved it", List.of("AUTH"))),
                        "gpt-4o", "v1", Instant.now().toString(), "corr-789"));

        AiSimilarCasesAssistResponse result = sut.similarCases(1L);

        assertThat(result.cases()).hasSize(1);
        assertThat(result.cases().get(0).ticketNo()).isEqualTo("TKT-0000050");
        assertThat(result.cases().get(0).similarityScore()).isEqualTo(0.92f);
        assertThat(result.metadata().available()).isTrue();
    }

    // ── Policy guidance ───────────────────────────────────────────────────────

    @Test
    void policyGuidance_returnsUnavailable_whenAiDisabled() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(false);

        AiPolicyGuidanceAssistResponse result = sut.policyGuidance(1L, "What is the refund policy?");

        assertThat(result.guidance()).isNull();
        assertThat(result.metadata().available()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SummaryContext stubSummaryContext() {
        return new SummaryContext("TKT-0000001", "Login issue", "IN_PROGRESS", "MEDIUM",
                "Acme Corp", null, null, List.of(), "OK",
                List.of(), List.of(), List.of(), "en");
    }

    private ReplyDraftContext stubReplyDraftContext() {
        return new ReplyDraftContext("TKT-0000001", "Login issue", "IN_PROGRESS", "MEDIUM",
                "Acme Corp", "I cannot login", "u***@acme.com",
                List.of(), "en", "professional");
    }

    private SimilarCasesContext stubSimilarCasesContext() {
        return new SimilarCasesContext("TKT-0000001", "Login issue",
                "User cannot log in after password change", List.of(), "ENTERPRISE");
    }

    private PolicyGuidanceContext stubPolicyGuidanceContext() {
        return new PolicyGuidanceContext("TKT-0000001", "Refund policy?",
                "Login issue", "IN_PROGRESS", List.of(), "Acme Corp", "en", List.of());
    }

    private static void setId(Ticket ticket, Long id) {
        try {
            var f = Ticket.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(ticket, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void setTicketNo(Ticket ticket, String ticketNo) {
        ticket.setTicketNo(ticketNo);
    }
}
