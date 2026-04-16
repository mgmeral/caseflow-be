package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service for similar-case retrieval.
 */
public record AiSimilarCasesRequest(
        String correlationId,
        String ticketNo,
        String subject,
        String problemSummary,
        List<String> tags,
        String customerCategory,
        /** When true, return only resolved/closed cases. */
        boolean resolvedOnly,
        int maxResults
) {}
