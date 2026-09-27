package com.caseflow.ai.service;

import java.util.List;

/**
 * Similar-case candidates for one ticket as returned by the AI service, one per source ticket,
 * best first — cached in {@code ticket_ai_response_cache} <em>before</em> per-agent visibility
 * filtering, so the cached value is identical for every agent.
 */
public record SimilarCaseCandidates(
        List<Candidate> candidates,
        String model,
        String promptVersion,
        String generatedAt
) {
    /** @param sourceId the candidate ticket's publicId, as indexed */
    public record Candidate(String sourceId, float score, String snippet, List<String> tags) {}
}
