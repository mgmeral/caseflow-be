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
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates synchronous AI assist flows for ticket-level operations.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Resolves the ticket from the repository</li>
 *   <li>Delegates context building to {@link TicketAiContextBuilder}</li>
 *   <li>Delegates HTTP calls to {@link CaseflowAiClient}</li>
 *   <li>Maps raw AI responses to stable FE-facing DTOs</li>
 *   <li>Returns graceful fallback responses when AI is unavailable</li>
 * </ul>
 *
 * <h2>Guarantees</h2>
 * This service NEVER throws to callers on AI failure. AI failures are captured and
 * returned as structured unavailable responses so ticket workflows are never blocked.
 */
@Service
public class AiAssistService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistService.class);
    private static final int SIMILAR_CASES_MAX = 5;

    private final CaseflowAiClient aiClient;
    private final TicketAiContextBuilder contextBuilder;
    private final AiAvailabilityService availabilityService;
    private final TicketRepository ticketRepository;

    public AiAssistService(CaseflowAiClient aiClient,
                           TicketAiContextBuilder contextBuilder,
                           AiAvailabilityService availabilityService,
                           TicketRepository ticketRepository) {
        this.aiClient = aiClient;
        this.contextBuilder = contextBuilder;
        this.availabilityService = availabilityService;
        this.ticketRepository = ticketRepository;
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AiSummaryAssistResponse summarize(Long ticketId) {
        String correlationId = newCorrelationId();
        Ticket ticket = requireTicket(ticketId);

        if (!availabilityService.isAvailable()) {
            return AiSummaryAssistResponse.unavailable(ticketId, correlationId, "AI service disabled");
        }

        try {
            SummaryContext ctx = contextBuilder.buildSummaryContext(ticket);
            AiSummaryRequest request = toSummaryRequest(ctx, correlationId);
            AiRawSummaryResponse raw = aiClient.requestSummary(request);

            return new AiSummaryAssistResponse(
                    ticketId,
                    raw.summary(),
                    com.caseflow.ai.api.dto.AiAssistMetadata.of(
                            raw.model(), raw.promptVersion(), raw.generatedAt(), correlationId));

        } catch (AiServiceUnavailableException ex) {
            log.warn("AI summary unavailable [ticketId={}, correlationId={}]: {}",
                    ticketId, correlationId, ex.getMessage());
            return AiSummaryAssistResponse.unavailable(ticketId, correlationId, "AI service unavailable");
        } catch (Exception ex) {
            log.error("AI summary unexpected error [ticketId={}, correlationId={}]",
                    ticketId, correlationId, ex);
            return AiSummaryAssistResponse.unavailable(ticketId, correlationId, "AI service error");
        }
    }

    // ── Reply draft ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AiReplyDraftAssistResponse replyDraft(Long ticketId, String toneHint) {
        String correlationId = newCorrelationId();
        Ticket ticket = requireTicket(ticketId);

        if (!availabilityService.isAvailable()) {
            return AiReplyDraftAssistResponse.unavailable(ticketId, correlationId, "AI service disabled");
        }

        try {
            ReplyDraftContext ctx = contextBuilder.buildReplyDraftContext(ticket);
            AiReplyDraftRequest request = toReplyDraftRequest(ctx, correlationId, toneHint);
            AiRawReplyDraftResponse raw = aiClient.requestReplyDraft(request);

            return new AiReplyDraftAssistResponse(
                    ticketId,
                    raw.draft(),
                    raw.toneApplied(),
                    com.caseflow.ai.api.dto.AiAssistMetadata.of(
                            raw.model(), raw.promptVersion(), raw.generatedAt(), correlationId));

        } catch (AiServiceUnavailableException ex) {
            log.warn("AI reply-draft unavailable [ticketId={}, correlationId={}]: {}",
                    ticketId, correlationId, ex.getMessage());
            return AiReplyDraftAssistResponse.unavailable(ticketId, correlationId, "AI service unavailable");
        } catch (Exception ex) {
            log.error("AI reply-draft unexpected error [ticketId={}, correlationId={}]",
                    ticketId, correlationId, ex);
            return AiReplyDraftAssistResponse.unavailable(ticketId, correlationId, "AI service error");
        }
    }

    // ── Similar cases ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AiSimilarCasesAssistResponse similarCases(Long ticketId) {
        String correlationId = newCorrelationId();
        Ticket ticket = requireTicket(ticketId);

        if (!availabilityService.isAvailable()) {
            return AiSimilarCasesAssistResponse.unavailable(ticketId, correlationId, "AI service disabled");
        }

        try {
            SimilarCasesContext ctx = contextBuilder.buildSimilarCasesContext(ticket);
            AiSimilarCasesRequest request = new AiSimilarCasesRequest(
                    correlationId, ctx.ticketNo(), ctx.subject(), ctx.problemSummary(),
                    ctx.tags(), ctx.customerCategory(), true, SIMILAR_CASES_MAX);
            AiRawSimilarCasesResponse raw = aiClient.requestSimilarCases(request);

            List<AiSimilarCasesAssistResponse.SimilarCase> cases = raw.cases() == null ? List.of() :
                    raw.cases().stream()
                            .map(c -> new AiSimilarCasesAssistResponse.SimilarCase(
                                    c.ticketNo(), c.subject(), c.similarityScore(),
                                    c.resolutionSummary(), c.tags()))
                            .toList();

            return new AiSimilarCasesAssistResponse(ticketId, cases,
                    com.caseflow.ai.api.dto.AiAssistMetadata.of(
                            raw.model(), raw.promptVersion(), raw.generatedAt(), correlationId));

        } catch (AiServiceUnavailableException ex) {
            log.warn("AI similar-cases unavailable [ticketId={}, correlationId={}]: {}",
                    ticketId, correlationId, ex.getMessage());
            return AiSimilarCasesAssistResponse.unavailable(ticketId, correlationId, "AI service unavailable");
        } catch (Exception ex) {
            log.error("AI similar-cases unexpected error [ticketId={}, correlationId={}]",
                    ticketId, correlationId, ex);
            return AiSimilarCasesAssistResponse.unavailable(ticketId, correlationId, "AI service error");
        }
    }

    // ── Policy guidance ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AiPolicyGuidanceAssistResponse policyGuidance(Long ticketId, String question) {
        String correlationId = newCorrelationId();
        Ticket ticket = requireTicket(ticketId);

        if (!availabilityService.isAvailable()) {
            return AiPolicyGuidanceAssistResponse.unavailable(ticketId, correlationId, "AI service disabled");
        }

        try {
            PolicyGuidanceContext ctx = contextBuilder.buildPolicyGuidanceContext(ticket, question);
            AiPolicyGuidanceRequest request = new AiPolicyGuidanceRequest(
                    correlationId, ctx.ticketNo(), ctx.userQuestion(), ctx.subject(),
                    ctx.status(), ctx.tags(), ctx.customerName(), ctx.locale(), ctx.scopeHints());
            AiRawPolicyGuidanceResponse raw = aiClient.requestPolicyGuidance(request);

            List<AiPolicyGuidanceAssistResponse.PolicyCitation> citations = raw.citations() == null
                    ? List.of()
                    : raw.citations().stream()
                            .map(c -> new AiPolicyGuidanceAssistResponse.PolicyCitation(
                                    c.policyId(), c.title(), c.excerpt(), c.relevanceScore()))
                            .toList();

            return new AiPolicyGuidanceAssistResponse(ticketId, raw.guidance(), citations,
                    com.caseflow.ai.api.dto.AiAssistMetadata.of(
                            raw.model(), raw.promptVersion(), raw.generatedAt(), correlationId));

        } catch (AiServiceUnavailableException ex) {
            log.warn("AI policy-guidance unavailable [ticketId={}, correlationId={}]: {}",
                    ticketId, correlationId, ex.getMessage());
            return AiPolicyGuidanceAssistResponse.unavailable(ticketId, correlationId, "AI service unavailable");
        } catch (Exception ex) {
            log.error("AI policy-guidance unexpected error [ticketId={}, correlationId={}]",
                    ticketId, correlationId, ex);
            return AiPolicyGuidanceAssistResponse.unavailable(ticketId, correlationId, "AI service error");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private AiSummaryRequest toSummaryRequest(SummaryContext ctx, String correlationId) {
        List<AiSummaryRequest.MessageSnippet> inbound = ctx.recentInboundMessages().stream()
                .map(m -> new AiSummaryRequest.MessageSnippet(m.from(), m.preview(), m.receivedAt()))
                .toList();
        List<AiSummaryRequest.MessageSnippet> outbound = ctx.recentOutboundMessages().stream()
                .map(m -> new AiSummaryRequest.MessageSnippet(m.from(), m.preview(), m.receivedAt()))
                .toList();
        return new AiSummaryRequest(correlationId, ctx.ticketNo(), ctx.subject(),
                ctx.status(), ctx.priority(), ctx.customerName(),
                ctx.assignedUserName(), ctx.assignedGroupName(),
                ctx.tags(), ctx.slaState(), inbound, outbound,
                ctx.recentInternalNotes(), ctx.locale());
    }

    private AiReplyDraftRequest toReplyDraftRequest(ReplyDraftContext ctx, String correlationId,
                                                     String toneHintOverride) {
        String tone = toneHintOverride != null ? toneHintOverride : ctx.toneHint();
        List<AiReplyDraftRequest.MessageSnippet> thread = ctx.threadContext().stream()
                .map(m -> new AiReplyDraftRequest.MessageSnippet(m.direction(), m.preview(), m.sentAt()))
                .toList();
        return new AiReplyDraftRequest(correlationId, ctx.ticketNo(), ctx.subject(),
                ctx.status(), ctx.priority(), ctx.customerName(),
                ctx.latestInboundMessage(), ctx.latestInboundFrom(), thread,
                List.of(), ctx.locale(), tone);
    }
}
