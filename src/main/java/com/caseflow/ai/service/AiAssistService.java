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
import com.caseflow.ai.domain.TicketAiIndex;
import com.caseflow.ai.domain.TicketAiResponseCache;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.ai.repository.TicketAiResponseCacheRepository;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Orchestrates synchronous AI assist flows for ticket-level operations.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Resolves the ticket from the repository</li>
 *   <li>Delegates context building to {@link TicketAiContextBuilder}</li>
 *   <li>Serves valid cache entries from {@code ticket_ai_response_cache} before calling the AI</li>
 *   <li>Delegates HTTP calls to {@link CaseflowAiClient}</li>
 *   <li>Maps raw AI responses to stable FE-facing DTOs and persists them in the cache</li>
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
    /** Chunk matches requested from the AI service; many collapse into one ticket or are not visible. */
    private static final int SIMILAR_CASES_TOP_K = 30;
    /** Distinct candidate tickets cached per ticket, before visibility filtering. */
    private static final int SIMILAR_CASE_CANDIDATES_KEPT = 15;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final CaseflowAiClient aiClient;
    private final TicketAiContextBuilder contextBuilder;
    private final AiAvailabilityService availabilityService;
    private final TicketRepository ticketRepository;
    private final TicketAiIndexRepository aiIndexRepository;
    private final TicketAiResponseCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    public AiAssistService(CaseflowAiClient aiClient,
                           TicketAiContextBuilder contextBuilder,
                           AiAvailabilityService availabilityService,
                           TicketRepository ticketRepository,
                           TicketAiIndexRepository aiIndexRepository,
                           TicketAiResponseCacheRepository cacheRepository,
                           ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.contextBuilder = contextBuilder;
        this.availabilityService = availabilityService;
        this.ticketRepository = ticketRepository;
        this.aiIndexRepository = aiIndexRepository;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    @Transactional
    public AiSummaryAssistResponse summarize(Long ticketId) {
        String correlationId = newCorrelationId();
        Ticket ticket = requireTicket(ticketId);

        if (!availabilityService.isAvailable()) {
            return AiSummaryAssistResponse.unavailable(ticketId, correlationId, "AI service disabled");
        }

        long sourceVersion = resolveSourceVersion(ticketId);
        Optional<AiSummaryAssistResponse> cached = loadCache(ticketId, sourceVersion,
                AiResponseType.SUMMARY, AiSummaryAssistResponse.class);
        if (cached.isPresent()) {
            log.debug("AI summary cache hit [ticketId={}, sourceVersion={}]", ticketId, sourceVersion);
            return cached.get();
        }

        try {
            SummaryContext ctx = contextBuilder.buildSummaryContext(ticket);
            AiSummaryRequest request = toSummaryRequest(ctx, correlationId);
            AiRawSummaryResponse raw = aiClient.requestSummary(request, ticketId);

            AiSummaryAssistResponse response = new AiSummaryAssistResponse(
                    ticketId,
                    raw.summary(),
                    raw.warnings() != null ? raw.warnings() : List.of(),
                    com.caseflow.ai.api.dto.AiAssistMetadata.of(
                            raw.model(), raw.promptVersion(), raw.generatedAt(), correlationId));

            saveCache(ticketId, sourceVersion, AiResponseType.SUMMARY, response,
                    raw.model(), raw.promptVersion(), parseInstant(raw.generatedAt()));
            return response;

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
            AiReplyDraftRequest request = toReplyDraftRequest(ctx, customerIdOf(ticket), correlationId, toneHint);
            AiRawReplyDraftResponse raw = aiClient.requestReplyDraft(request, ticketId);

            return new AiReplyDraftAssistResponse(
                    ticketId,
                    raw.suggestedBody(),
                    raw.tone(),
                    raw.warnings() != null ? raw.warnings() : List.of(),
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

    /**
     * Similar resolved/closed tickets the caller may read.
     *
     * <p>Candidates come from the AI service (the ticket itself excluded) and are cached per
     * ticket <em>before</em> any visibility filtering, so the cache is safe to share between
     * agents. Every request then keeps only candidates {@code visibleToCaller} accepts, checked
     * against the current database state — assignment can change after a ticket is indexed.
     */
    @Transactional
    public AiSimilarCasesAssistResponse similarCases(Long ticketId, Predicate<Ticket> visibleToCaller) {
        String correlationId = newCorrelationId();
        Ticket ticket = requireTicket(ticketId);

        if (!availabilityService.isAvailable()) {
            return AiSimilarCasesAssistResponse.unavailable(ticketId, correlationId, "AI service disabled");
        }

        try {
            long sourceVersion = resolveSourceVersion(ticketId);
            SimilarCaseCandidates candidates = loadCache(ticketId, sourceVersion,
                    AiResponseType.SIMILAR_CASES, SimilarCaseCandidates.class)
                    .filter(c -> c.candidates() != null)
                    .orElse(null);
            if (candidates == null) {
                SimilarCasesContext ctx = contextBuilder.buildSimilarCasesContext(ticket);
                // Matches are per chunk and some will be invisible to the caller, so over-fetch.
                AiSimilarCasesRequest request = new AiSimilarCasesRequest(
                        correlationId, similarCasesQuery(ctx), null, ctx.tags(), SIMILAR_CASES_TOP_K,
                        new AiSimilarCasesRequest.Filters(
                                AiTicketSyncService.INDEXED_STATUSES.stream().map(Enum::name).sorted().toList(),
                                List.of(ticket.getPublicId().toString())));
                AiRawSimilarCasesResponse raw = aiClient.requestSimilarCases(request, ticketId);
                candidates = toCandidates(raw);
                saveCache(ticketId, sourceVersion, AiResponseType.SIMILAR_CASES, candidates,
                        raw.model(), raw.promptVersion(), parseInstant(raw.generatedAt()));
            } else {
                log.debug("AI similar-cases cache hit [ticketId={}, sourceVersion={}]", ticketId, sourceVersion);
            }

            return new AiSimilarCasesAssistResponse(ticketId, visibleCases(candidates, visibleToCaller),
                    com.caseflow.ai.api.dto.AiAssistMetadata.of(
                            candidates.model(), candidates.promptVersion(), candidates.generatedAt(), correlationId));

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
                    correlationId, ctx.userQuestion(), ctx.customerName(), customerIdOf(ticket),
                    ctx.status(), ctx.priority(), ctx.tags(), null);
            AiRawPolicyGuidanceResponse raw = aiClient.requestPolicyGuidance(request, ticketId);

            List<AiPolicyGuidanceAssistResponse.PolicyCitation> citations = raw.policyReferences() == null
                    ? List.of()
                    : raw.policyReferences().stream()
                            .map(r -> new AiPolicyGuidanceAssistResponse.PolicyCitation(
                                    r.sourceId(), r.title(), r.snippet(),
                                    r.score() != null ? r.score().floatValue() : 0f))
                            .toList();

            return new AiPolicyGuidanceAssistResponse(ticketId, raw.answer(), citations,
                    raw.recommendedActions() != null ? raw.recommendedActions() : List.of(),
                    raw.confidence(),
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

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private long resolveSourceVersion(Long ticketId) {
        return aiIndexRepository.findByTicketId(ticketId)
                .map(TicketAiIndex::getSourceVersion)
                .orElse(0L);
    }

    private <T> Optional<T> loadCache(Long ticketId, long sourceVersion,
                                       AiResponseType type, Class<T> responseClass) {
        return cacheRepository.findFirstByTicketIdAndSourceVersionAndResponseTypeAndIsStaleIsFalse(ticketId, sourceVersion, type)
                .filter(TicketAiResponseCache::isValid)
                .map(entry -> {
                    try {
                        return objectMapper.readValue(entry.getResponsePayload(), responseClass);
                    } catch (JsonProcessingException e) {
                        log.warn("AI cache deserialization failed [ticketId={}, type={}]: {}",
                                ticketId, type, e.getMessage());
                        entry.invalidate();
                        cacheRepository.save(entry);
                        return null;
                    }
                });
    }

    private void saveCache(Long ticketId, long sourceVersion, AiResponseType type,
                           Object response, String model, String promptVersion, Instant generatedAt) {
        try {
            String payload = objectMapper.writeValueAsString(response);

            // Upsert by overwriting the live row in place. Marking it stale and inserting a new row
            // breaks: Hibernate flushes the INSERT before the UPDATE, so the new row collides with
            // the not-yet-stale old one on idx_ai_cache_ticket_version_type and the request fails.
            TicketAiResponseCache entry = cacheRepository
                    .findFirstByTicketIdAndSourceVersionAndResponseTypeAndIsStaleIsFalse(ticketId, sourceVersion, type)
                    .orElseGet(() -> {
                        TicketAiResponseCache fresh = new TicketAiResponseCache();
                        fresh.setTicketId(ticketId);
                        fresh.setSourceVersion(sourceVersion);
                        fresh.setResponseType(type);
                        return fresh;
                    });
            entry.setResponsePayload(payload);
            entry.setModelName(model);
            entry.setPromptVersion(promptVersion);
            entry.setGeneratedAt(generatedAt);
            entry.setExpiresAt(Instant.now().plus(CACHE_TTL));
            cacheRepository.save(entry);
        } catch (JsonProcessingException e) {
            log.warn("AI cache serialization failed [ticketId={}, type={}]: {}", ticketId, type, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Ticket requireTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private static String similarCasesQuery(SimilarCasesContext ctx) {
        String subject = ctx.subject() != null ? ctx.subject() : "";
        String problem = ctx.problemSummary() != null ? ctx.problemSummary() : "";
        String query = (subject + "\n" + problem).strip();
        // The AI service rejects a blank queryText; fall back to the ticket number.
        return query.isEmpty() ? ctx.ticketNo() : query;
    }

    /**
     * Collapses per-chunk matches into one candidate per source ticket (first = highest score,
     * since the AI service returns matches in descending score order).
     */
    private static SimilarCaseCandidates toCandidates(AiRawSimilarCasesResponse raw) {
        Map<String, SimilarCaseCandidates.Candidate> bySource = new LinkedHashMap<>();
        if (raw.matches() != null) {
            for (AiRawSimilarCasesResponse.Match m : raw.matches()) {
                if (m.sourceId() == null || m.sourceId().isBlank()) continue;
                Map<String, Object> meta = m.metadata() != null ? m.metadata() : Map.of();
                bySource.putIfAbsent(m.sourceId(), new SimilarCaseCandidates.Candidate(
                        m.sourceId(),
                        m.score() != null ? m.score().floatValue() : 0f,
                        m.snippet(),
                        splitTags(meta.get("tags"))));
            }
        }
        return new SimilarCaseCandidates(bySource.values().stream().limit(SIMILAR_CASE_CANDIDATES_KEPT).toList(),
                raw.model(), raw.promptVersion(), raw.generatedAt());
    }

    /**
     * Candidates the caller may read, best first, with ticket number and subject taken from the
     * database (not from the index, which may be stale). Candidates whose ticket no longer
     * exists, or whose sourceId is not a ticket publicId, are dropped.
     */
    private List<AiSimilarCasesAssistResponse.SimilarCase> visibleCases(SimilarCaseCandidates candidates,
                                                                        Predicate<Ticket> visibleToCaller) {
        Map<UUID, SimilarCaseCandidates.Candidate> byPublicId = new LinkedHashMap<>();
        for (SimilarCaseCandidates.Candidate c : candidates.candidates()) {
            try {
                byPublicId.putIfAbsent(UUID.fromString(c.sourceId()), c);
            } catch (IllegalArgumentException notATicketPublicId) {
                // indexed by something other than caseflow-be — ignore
            }
        }
        if (byPublicId.isEmpty()) return List.of();
        Map<UUID, Ticket> tickets = new HashMap<>();
        ticketRepository.findByPublicIdIn(byPublicId.keySet()).forEach(t -> tickets.put(t.getPublicId(), t));

        return byPublicId.entrySet().stream()
                .filter(e -> tickets.containsKey(e.getKey()))
                .filter(e -> visibleToCaller.test(tickets.get(e.getKey())))
                .limit(SIMILAR_CASES_MAX)
                .map(e -> {
                    Ticket t = tickets.get(e.getKey());
                    SimilarCaseCandidates.Candidate c = e.getValue();
                    return new AiSimilarCasesAssistResponse.SimilarCase(
                            t.getId(), t.getTicketNo(), t.getSubject(), c.score(), c.snippet(), c.tags());
                })
                .toList();
    }

    /** The AI service stores tags as one comma-separated metadata string. */
    private static List<String> splitTags(Object value) {
        if (!(value instanceof String s) || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
    }

    private static String customerIdOf(Ticket ticket) {
        return ticket.getCustomerId() != null ? ticket.getCustomerId().toString() : null;
    }

    private String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (Exception e) { return null; }
    }

    private AiSummaryRequest toSummaryRequest(SummaryContext ctx, String correlationId) {
        List<AiSummaryRequest.LatestMessage> inbound = ctx.recentInboundMessages().stream()
                .map(m -> new AiSummaryRequest.LatestMessage("inbound", m.from(), m.preview(), m.receivedAt()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<AiSummaryRequest.LatestMessage> outbound = ctx.recentOutboundMessages().stream()
                .map(m -> new AiSummaryRequest.LatestMessage("outbound", m.from(), m.preview(), m.receivedAt()))
                .toList();
        inbound.addAll(outbound);
        inbound.sort(Comparator.comparing(
                m -> m.sentAt() != null ? m.sentAt() : "", Comparator.naturalOrder()));

        return new AiSummaryRequest(
                correlationId,
                ctx.customerName(),
                ctx.status(),
                ctx.priority(),
                ctx.slaState(),
                ctx.tags(),
                inbound,
                ctx.recentInternalNotes(),
                ctx.locale(),
                "STANDARD"
        );
    }

    private AiReplyDraftRequest toReplyDraftRequest(ReplyDraftContext ctx, String customerId, String correlationId,
                                                     String toneHintOverride) {
        String tone = toneHintOverride != null ? toneHintOverride : ctx.toneHint();
        List<AiReplyDraftRequest.LatestMessage> latestMessages = ctx.threadContext().stream()
                .map(m -> new AiReplyDraftRequest.LatestMessage(m.direction(), null, m.preview(), m.sentAt()))
                .toList();
        return new AiReplyDraftRequest(
                correlationId,
                ctx.customerName(),
                customerId,
                ctx.locale(),
                tone,
                ctx.status(),
                ctx.priority(),
                ctx.tags(),
                latestMessages,
                ctx.internalNotes(),
                ctx.policySnippets(),
                ctx.constraints(),
                "RESOLUTION",
                ctx.selectedTemplateCode()
        );
    }
}
