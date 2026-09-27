package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service's {@code POST /api/ai/tickets/{ticketId}/similar-cases}.
 *
 * <p>Mirrors {@code caseflow-ai-service}'s {@code SimilarCasesRequest}; {@code queryText} is
 * required there. {@code correlationId} is not part of the downstream body — it travels in the
 * {@code X-Correlation-ID} header — but is kept here for logging, consistent with the other requests.
 */
public record AiSimilarCasesRequest(
        String correlationId,
        String queryText,
        String customerName,
        List<String> tags,
        Integer topK,
        Filters filters
) {
    /**
     * Enforced by the AI service on the vector search. Agent visibility is deliberately NOT
     * pushed down here — it depends on current assignment and is checked in caseflow-be.
     */
    public record Filters(List<String> statuses, List<String> excludeSourceIds) {}
}
