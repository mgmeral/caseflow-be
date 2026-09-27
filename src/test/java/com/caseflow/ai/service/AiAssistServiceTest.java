package com.caseflow.ai.service;

import com.caseflow.ai.api.dto.AiPolicyGuidanceAssistResponse;
import com.caseflow.ai.api.dto.AiReplyDraftAssistResponse;
import com.caseflow.ai.api.dto.AiSimilarCasesAssistResponse;
import com.caseflow.ai.api.dto.AiSummaryAssistResponse;
import com.caseflow.ai.client.AiServiceUnavailableException;
import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.request.AiPolicyGuidanceRequest;
import com.caseflow.ai.client.dto.request.AiReplyDraftRequest;
import com.caseflow.ai.client.dto.request.AiSimilarCasesRequest;
import com.caseflow.ai.client.dto.request.AiSummaryRequest;
import com.caseflow.ai.client.dto.response.AiRawPolicyGuidanceResponse;
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
import com.caseflow.ai.domain.TicketAiResponseCache;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

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
        setPublicId(ticket, UUID.fromString("00000000-0000-0000-0000-000000000001"));

        // Default: no AI index entry (sourceVersion=0) and no cache hits
        // lenient() because not all tests call cache-enabled methods (replyDraft / policyGuidance skip cache)
        lenient().when(aiIndexRepository.findByTicketId(anyLong())).thenReturn(Optional.empty());
        lenient().when(cacheRepository.findFirstByTicketIdAndSourceVersionAndResponseTypeAndIsStaleIsFalse(anyLong(), anyLong(), any(AiResponseType.class)))
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
    void summarize_overwritesAnExpiredCacheRowInPlace_insteadOfInsertingASecondOne() {
        // Regression: invalidating the old row and inserting a new one made Hibernate flush the
        // INSERT first, which collided with the still-live row on idx_ai_cache_ticket_version_type.
        TicketAiResponseCache expired = new TicketAiResponseCache();
        expired.setTicketId(1L);
        expired.setSourceVersion(0L);
        expired.setResponseType(AiResponseType.SUMMARY);
        expired.setResponsePayload("{}");
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(cacheRepository.findFirstByTicketIdAndSourceVersionAndResponseTypeAndIsStaleIsFalse(1L, 0L, AiResponseType.SUMMARY))
                .thenReturn(Optional.of(expired));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSummaryContext(ticket)).thenReturn(stubSummaryContext());
        when(aiClient.requestSummary(any(), eq(1L))).thenReturn(
                new AiRawSummaryResponse("Fresh summary.", null, "qwen", "v1", Instant.now().toString(), "corr-1"));

        AiSummaryAssistResponse result = sut.summarize(1L);

        assertThat(result.summary()).isEqualTo("Fresh summary.");
        ArgumentCaptor<TicketAiResponseCache> saved = ArgumentCaptor.forClass(TicketAiResponseCache.class);
        verify(cacheRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(expired);
        assertThat(saved.getValue().isValid()).isTrue();
        assertThat(saved.getValue().getResponsePayload()).contains("Fresh summary.");
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

    private static final Predicate<Ticket> EVERYONE_VISIBLE = t -> true;

    @Test
    void similarCases_returnsEmptyList_whenAiDisabled() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(false);

        AiSimilarCasesAssistResponse result = sut.similarCases(1L, EVERYONE_VISIBLE);

        assertThat(result.cases()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void similarCases_returnsEmptyList_whenAiClientThrows() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSimilarCasesContext(ticket)).thenReturn(stubSimilarCasesContext());
        when(aiClient.requestSimilarCases(any(), eq(1L)))
                .thenThrow(new AiServiceUnavailableException("/api/ai/tickets/1/similar-cases", "timeout"));

        AiSimilarCasesAssistResponse result = sut.similarCases(1L, EVERYONE_VISIBLE);

        assertThat(result.cases()).isEmpty();
        assertThat(result.metadata().available()).isFalse();
    }

    @Test
    void similarCases_returnsCases_withNumberAndSubjectFromDatabase() {
        Ticket similar = indexedTicket(50L, "TKT-0000050", "Login fails after reset", 7L);
        stubSimilarCases(List.of(match(similar.getPublicId().toString(), "stale indexed title", 0.92,
                Map.of("tags", "AUTH, LOGIN"))), List.of(similar));

        AiSimilarCasesAssistResponse result = sut.similarCases(1L, EVERYONE_VISIBLE);

        assertThat(result.cases()).singleElement().satisfies(c -> {
            assertThat(c.ticketId()).isEqualTo(50L);
            assertThat(c.ticketNo()).isEqualTo("TKT-0000050");
            assertThat(c.subject()).isEqualTo("Login fails after reset");
            assertThat(c.similarityScore()).isEqualTo(0.92f);
            assertThat(c.tags()).containsExactly("AUTH", "LOGIN");
            assertThat(c.resolutionSummary()).startsWith("snippet of");
        });
        assertThat(result.metadata().available()).isTrue();
    }

    @Test
    void similarCases_dropsTicketsTheCallerCannotRead() {
        Ticket mine = indexedTicket(50L, "TKT-50", "Mine", 7L);
        Ticket otherGroup = indexedTicket(60L, "TKT-60", "Other group", 8L);
        stubSimilarCases(List.of(
                match(otherGroup.getPublicId().toString(), "x", 0.95, Map.of()),
                match(mine.getPublicId().toString(), "y", 0.90, Map.of())), List.of(mine, otherGroup));

        AiSimilarCasesAssistResponse result = sut.similarCases(1L, t -> Long.valueOf(7L).equals(t.getAssignedGroupId()));

        assertThat(result.cases()).extracting(AiSimilarCasesAssistResponse.SimilarCase::ticketId).containsExactly(50L);
    }

    @Test
    void similarCases_cachesCandidatesBeforeVisibilityFiltering() throws Exception {
        Ticket mine = indexedTicket(50L, "TKT-50", "Mine", 7L);
        Ticket otherGroup = indexedTicket(60L, "TKT-60", "Other group", 8L);
        stubSimilarCases(List.of(
                match(otherGroup.getPublicId().toString(), "x", 0.95, Map.of()),
                match(mine.getPublicId().toString(), "y", 0.90, Map.of())), List.of(mine, otherGroup));

        sut.similarCases(1L, t -> Long.valueOf(7L).equals(t.getAssignedGroupId()));

        ArgumentCaptor<TicketAiResponseCache> saved = ArgumentCaptor.forClass(TicketAiResponseCache.class);
        verify(cacheRepository).save(saved.capture());
        SimilarCaseCandidates cached = objectMapper.readValue(saved.getValue().getResponsePayload(),
                SimilarCaseCandidates.class);
        // Both candidates are cached — the next agent may be allowed to see the other one.
        assertThat(cached.candidates()).hasSize(2);
    }

    @Test
    void similarCases_collapsesChunksOfSameSource_andCapsResults() {
        List<Ticket> tickets = new ArrayList<>();
        List<AiRawSimilarCasesResponse.Match> matches = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Ticket t = indexedTicket(100L + i, "TKT-" + i, "Subject " + i, 7L);
            tickets.add(t);
            matches.add(match(t.getPublicId().toString(), "x", 0.99 - i * 0.01, Map.of()));
            if (i == 1) matches.add(match(t.getPublicId().toString(), "x", 0.5, Map.of()));  // 2nd chunk
        }
        stubSimilarCases(matches, tickets);

        AiSimilarCasesAssistResponse result = sut.similarCases(1L, EVERYONE_VISIBLE);

        assertThat(result.cases()).extracting(AiSimilarCasesAssistResponse.SimilarCase::ticketNo)
                .containsExactly("TKT-1", "TKT-2", "TKT-3", "TKT-4", "TKT-5");
        assertThat(result.cases().get(0).similarityScore()).isEqualTo(0.98f);
    }

    @Test
    void similarCases_asksForSettledTicketsOnly_excludingItself() {
        stubSimilarCases(List.of(), List.of());

        sut.similarCases(1L, EVERYONE_VISIBLE);

        ArgumentCaptor<AiSimilarCasesRequest> captor = ArgumentCaptor.forClass(AiSimilarCasesRequest.class);
        verify(aiClient).requestSimilarCases(captor.capture(), eq(1L));
        AiSimilarCasesRequest sent = captor.getValue();
        assertThat(sent.queryText()).isEqualTo("Login issue\nUser cannot log in after password change");
        assertThat(sent.topK()).isGreaterThan(5);
        assertThat(sent.filters().statuses()).containsExactly("CLOSED", "RESOLVED");
        assertThat(sent.filters().excludeSourceIds()).containsExactly(ticket.getPublicId().toString());
    }

    @Test
    void similarCases_ignoresCandidatesThatAreNotTicketsAnyMore() {
        stubSimilarCases(List.of(
                match(UUID.randomUUID().toString(), "deleted ticket", 0.9, Map.of()),
                match("not-a-uuid", "foreign source", 0.8, Map.of())), List.of());

        assertThat(sut.similarCases(1L, EVERYONE_VISIBLE).cases()).isEmpty();
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

    @Test
    void policyGuidance_mapsAnswerReferencesAndActions_onSuccess() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildPolicyGuidanceContext(ticket, "Refund policy?"))
                .thenReturn(stubPolicyGuidanceContext());
        when(aiClient.requestPolicyGuidance(any(), eq(1L))).thenReturn(new AiRawPolicyGuidanceResponse(
                "req-1", "1", "Refunds within 30 days.", List.of("Offer refund"), 0.8,
                List.of(new AiRawPolicyGuidanceResponse.PolicyReference("pol-1", "Refund policy", "Refunds...", 0.77)),
                List.of(), "llama3.1", "1.0", "2026-09-27T10:00:00Z"));

        AiPolicyGuidanceAssistResponse result = sut.policyGuidance(1L, "Refund policy?");

        assertThat(result.guidance()).isEqualTo("Refunds within 30 days.");
        assertThat(result.recommendedActions()).containsExactly("Offer refund");
        assertThat(result.confidence()).isEqualTo(0.8);
        assertThat(result.citations()).singleElement().satisfies(c -> {
            assertThat(c.policyId()).isEqualTo("pol-1");
            assertThat(c.excerpt()).isEqualTo("Refunds...");
            assertThat(c.relevanceScore()).isEqualTo(0.77f);
        });
        assertThat(result.metadata().available()).isTrue();

        ArgumentCaptor<AiPolicyGuidanceRequest> captor = ArgumentCaptor.forClass(AiPolicyGuidanceRequest.class);
        verify(aiClient).requestPolicyGuidance(captor.capture(), eq(1L));
        assertThat(captor.getValue().query()).isEqualTo("Refund policy?");
        assertThat(captor.getValue().ticketStatus()).isEqualTo("IN_PROGRESS");
        assertThat(captor.getValue().priority()).isEqualTo("MEDIUM");
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
                "Login issue", "IN_PROGRESS", "MEDIUM", List.of(), "Acme Corp", "en");
    }

    private static AiRawSimilarCasesResponse similarCasesResponse(List<AiRawSimilarCasesResponse.Match> matches) {
        return new AiRawSimilarCasesResponse("req-1", "1", matches, List.of(),
                "llama3.1", "1.0", Instant.now().toString());
    }

    private static AiRawSimilarCasesResponse.Match match(String sourceId, String title, double score,
                                                          Map<String, Object> metadata) {
        return new AiRawSimilarCasesResponse.Match(sourceId, "TICKET", title,
                "snippet of " + sourceId, score, metadata);
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

    private static void setPublicId(Ticket ticket, UUID publicId) {
        try {
            var f = Ticket.class.getDeclaredField("publicId");
            f.setAccessible(true);
            f.set(ticket, publicId);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** A resolved ticket as it exists in the database, in group {@code groupId}. */
    private static Ticket indexedTicket(Long id, String ticketNo, String subject, Long groupId) {
        Ticket t = new Ticket();
        setId(t, id);
        setPublicId(t, UUID.randomUUID());
        t.setTicketNo(ticketNo);
        t.setSubject(subject);
        t.setStatus(TicketStatus.RESOLVED);
        t.setAssignedGroupId(groupId);
        return t;
    }

    private void stubSimilarCases(List<AiRawSimilarCasesResponse.Match> matches, List<Ticket> existing) {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(availabilityService.isAvailable()).thenReturn(true);
        when(contextBuilder.buildSimilarCasesContext(ticket)).thenReturn(stubSimilarCasesContext());
        when(aiClient.requestSimilarCases(any(), eq(1L))).thenReturn(similarCasesResponse(matches));
        lenient().when(ticketRepository.findByPublicIdIn(any())).thenReturn(existing);
    }
}
