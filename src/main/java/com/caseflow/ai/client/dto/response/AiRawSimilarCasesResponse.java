package com.caseflow.ai.client.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Raw response from the AI service's similar-cases endpoint.
 *
 * <p>Mirrors {@code caseflow-ai-service}'s {@code SimilarCasesResponse}. Each match is one vector
 * chunk, so the same {@code sourceId} can appear more than once.
 */
public record AiRawSimilarCasesResponse(
        String requestId,
        String ticketId,
        List<Match> matches,
        List<String> warnings,
        String model,
        String promptVersion,
        String generatedAt
) {
    public record Match(
            String sourceId,
            String sourceType,
            String title,
            String snippet,
            Double score,
            Map<String, Object> metadata
    ) {}
}
