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
        Integer topK
) {}
