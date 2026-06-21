package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.AiPolicyGuidanceAssistResponse;
import com.caseflow.ai.api.dto.AiReplyDraftAssistResponse;
import com.caseflow.ai.api.dto.AiSimilarCasesAssistResponse;
import com.caseflow.ai.api.dto.AiSummaryAssistResponse;
import com.caseflow.ai.client.AiServiceUnavailableException;
import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.request.AiReplyDraftRequest;
import com.caseflow.ai.client.dto.request.AiSummaryRequest;
import com.caseflow.ai.client.dto.response.AiRawReplyDraftResponse;
import com.caseflow.ai.client.dto.response.AiRawSimilarCasesResponse;
import com.caseflow.ai.client.dto.response.AiRawSummaryResponse;
import com.caseflow.ai.context.TicketAiContextBuilder;
import com.caseflow.ai.context.dto.PolicyGuidanceContext;
import com.caseflow.ai.context.dto.ReplyDraftContext;
import com.caseflow.ai.context.dto.SimilarCasesContext;
import com.caseflow.ai.context.dto.SummaryContext;
import com.caseflow.ai.domain.AiResponseType;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.ai.repository.TicketAiResponseCacheRepository;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistServiceTest {

    @Mock private CaseflowAiClient aiClient;
    @Mock private TicketAiContextBuilder contextBuilder;
    @Mock private AiAvailabilityService availabilityService;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketAiIndexRepository aiIndexRepository;
    @Mock private TicketAiResponseCacheRepository cacheRepository;
    @Spy  private ObjectMapper objectMapper;

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

        // Default: no AI index entry (sourceVersion=0) and no cache hits
        // lenient() because not all tests call cache-enabled methods (replyDraft / policyGuidance skip cache)
        lenient().when(aiIndexRepository.findByTicketId(anyLong())).thenReturn(Optional.empty());
        lenient().when(cacheRepository.findByTicketIdAndSourceVersionAndResponseType(anyLong(), anyLong(), any(AiResponseType.class)))
                .thenReturn(Optional.empty());
        lenient().when(cacheRepository.save(any())).thenAnswer(i -> i.getArgument(0));
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
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
        assertThat(result.metadata().unavailableReason()).isEqualTo("AI service disabled");
        verify(aiClient, never()).requestSummary(any(), any());
    }

    @Test
    void summarize_returnsUnavailable_whenAiClientThrows() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any(), eq(1L)))
                .thenThrow(new AiServiceUnavailableException("/api/ai/tickets/1/summary", "timeout"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isNull();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
        assertThat(result.metadata().unavailableReason()).isEqualTo("AI service unavailable");
    }

    @Test
    void summarize_returnsUnavailable_onUnexpectedError() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any(), eq(1L))).thenThrow(new RuntimeException("unexpected"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isNull();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void summarize_returnsResponse_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any(), eq(1L))).thenReturn(
                new AiRawSummaryResponse("Issue summary here.", null,
                        "gpt-4o", "v1", Instant.now().toString(), "corr-123"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isEqualTo("Issue summary here.");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isTrue();
        assertThat(result.metadata().model()).isEqualTo("gpt-4o");
        assertThat(result.ticketId()).isEqualTo(1L);
    }

    @Test
    void summarize_propagatesWarnings_fromRawResponse() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any(), eq(1L))).thenReturn(
                new AiRawSummaryResponse("Summary with caveats.", List.of("LOW_MESSAGE_COUNT"),
                        "gpt-4o", "v1", Instant.now().toString(), "corr-123"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.warnings()).containsExactly("LOW_MESSAGE_COUNT");
        assertThat(result.metadata().available()).isTrue();
    }

    @Test
    void summarize_passesTicketId_toClient() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any(), eq(1L))).thenReturn(
                new AiRawSummaryResponse("ok", null, "gpt-4o", "v1", Instant.now().toString(), "c1"));

        sut.summarize(1L);

        ArgumentCaptor<Long> ticketIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(aiClient).requestSummary(any(), ticketIdCaptor.capture());
        assertThat(ticketIdCaptor.getValue()).isEqualTo(1L);
    }

    @Test
    void summarize_buildsUnifiedLatestMessages_forDownstreamRequest() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        // Context with one inbound and one outbound message
        SummaryContext ctx = new SummaryContext("TKT-0000001", "Login issue", "IN_PROGRESS", "MEDIUM",
                "Acme Corp", null, null, List.of(), "OK",
                List.of(new SummaryContext.MessageSnippet("c***@acme.com", "I cannot login", "2026-04-16T10:00:00Z")),
                List.of(new SummaryContext.MessageSnippet("agent", "We are looking into it", "2026-04-16T10:05:00Z")),
                List.of(), "en");
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(ctx);
        when(aiClient.requestSummary(any(), eq(1L))).thenReturn(
                new AiRawSummaryResponse("ok", null, "gpt-4o", "v1", Instant.now().toString(), "c1"));

        sut.summarize(1L);

        ArgumentCaptor<AiSummaryRequest> reqCaptor = ArgumentCaptor.forClass(AiSummaryRequest.class);
        verify(aiClient).requestSummary(reqCaptor.capture(), eq(1L));
        AiSummaryRequest sent = reqCaptor.getValue();
        assertThat(sent.latestMessages()).hasSize(2);
        assertThat(sent.latestMessages().get(0).direction()).isEqualTo("inbound");
        assertThat(sent.latestMessages().get(1).direction()).isEqualTo("outbound");
        assertThat(sent.summaryStyle()).isEqualTo("STANDARD");
        assertThat(sent.ticketStatus()).isEqualTo("IN_PROGRESS");
    }

    // ── Reply draft ───────────────────────────────────────────────────────────

    @Test
    void replyDraft_returnsUnavailable_whenAiDisabled() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(false);

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, null);

        assertThat(result.draft()).isNull();
        assertThat(result.toneApplied()).isNull();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
        verify(aiClient, never()).requestReplyDraft(any(), any());
    }

    @Test
    void replyDraft_returnsUnavailable_whenAiClientThrows() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any(), eq(1L)))
                .thenThrow(new AiServiceUnavailableException("/api/ai/tickets/1/reply-draft", 503, "service unavailable"));

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, null);

        assertThat(result.draft()).isNull();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void replyDraft_returnsResponse_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any(), eq(1L))).thenReturn(
                new AiRawReplyDraftResponse("Dear customer, ...", "professional",
                        null, "gpt-4o", "v1", Instant.now().toString(), "corr-456"));

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, "empathetic");

        assertThat(result.draft()).isEqualTo("Dear customer, ...");
        assertThat(result.toneApplied()).isEqualTo("professional");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.metadata().available()).isTrue();
    }

    @Test
    void replyDraft_withToneHint_overridesTone() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any(), eq(1L))).thenReturn(
                new AiRawReplyDraftResponse("Draft text", "empathetic",
                        null, "gpt-4o", "v1", Instant.now().toString(), "c2"));

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, "empathetic");

        ArgumentCaptor<AiReplyDraftRequest> reqCaptor = ArgumentCaptor.forClass(AiReplyDraftRequest.class);
        verify(aiClient).requestReplyDraft(reqCaptor.capture(), eq(1L));
        assertThat(reqCaptor.getValue().tone()).isEqualTo("empathetic");
        assertThat(result.toneApplied()).isEqualTo("empathetic");
    }

    @Test
    void replyDraft_usesDefaultTone_whenNoToneHintProvided() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any(), eq(1L))).thenReturn(
                new AiRawReplyDraftResponse("Draft text", "professional",
                        null, "gpt-4o", "v1", Instant.now().toString(), "c3"));

        sut.replyDraft(1L, null);

        ArgumentCaptor<AiReplyDraftRequest> reqCaptor = ArgumentCaptor.forClass(AiReplyDraftRequest.class);
        verify(aiClient).requestReplyDraft(reqCaptor.capture(), eq(1L));
        // default tone from context ("professional") should be used when no override given
        assertThat(reqCaptor.getValue().tone()).isEqualTo("professional");
    }

    @Test
    void replyDraft_propagatesWarnings_fromRawResponse() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any(), eq(1L))).thenReturn(
                new AiRawReplyDraftResponse("Draft text", "professional",
                        List.of("TEMPLATE_FALLBACK_USED"), "gpt-4o", "v1",
                        Instant.now().toString(), "c4"));

        AiReplyDraftAssistResponse result = sut.replyDraft(1L, null);

        assertThat(result.warnings()).containsExactly("TEMPLATE_FALLBACK_USED");
        assertThat(result.metadata().available()).isTrue();
    }

    @Test
    void replyDraft_passesTicketId_toClient() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(stubReplyDraftContext());
        when(aiClient.requestReplyDraft(any(), eq(1L))).thenReturn(
                new AiRawReplyDraftResponse("ok", "professional", null,
                        "gpt-4o", "v1", Instant.now().toString(), "c5"));

        sut.replyDraft(1L, null);

        ArgumentCaptor<Long> ticketIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(aiClient).requestReplyDraft(any(), ticketIdCaptor.capture());
        assertThat(ticketIdCaptor.getValue()).isEqualTo(1L);
    }

    @Test
    void replyDraft_mapsDownstreamFieldsCorrectly() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        ReplyDraftContext ctx = new ReplyDraftContext("TKT-0000001", "Login issue", "IN_PROGRESS", "MEDIUM",
                "Acme Corp", "I cannot login", "c***@acme.com",
                List.of(new ReplyDraftContext.MessageSnippet("inbound", "I cannot login", "2026-04-16T10:00:00Z")),
                List.of("AUTH", "LOGIN"), List.of("Agent note about account"), "en", "professional",
                List.of(), List.of(), null);
        when(contextBuilder.buildReplyDraftContext(ticket)).thenReturn(ctx);
        when(aiClient.requestReplyDraft(any(), eq(1L))).thenReturn(
                new AiRawReplyDraftResponse("ok", "professional", null,
                        "gpt-4o", "v1", Instant.now().toString(), "c6"));

        sut.replyDraft(1L, null);

        ArgumentCaptor<AiReplyDraftRequest> reqCaptor = ArgumentCaptor.forClass(AiReplyDraftRequest.class);
        verify(aiClient).requestReplyDraft(reqCaptor.capture(), eq(1L));
        AiReplyDraftRequest sent = reqCaptor.getValue();
        assertThat(sent.ticketStatus()).isEqualTo("IN_PROGRESS");
        assertThat(sent.tags()).containsExactly("AUTH", "LOGIN");
        assertThat(sent.internalNotes()).containsExactly("Agent note about account");
        assertThat(sent.replyGoal()).isEqualTo("RESOLUTION");
        assertThat(sent.selectedTemplateCode()).isNull();
        assertThat(sent.latestMessages()).hasSize(1);
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
                List.of(), List.of(), List.of(), "en", "professional",
                List.of(), List.of(), null);
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
