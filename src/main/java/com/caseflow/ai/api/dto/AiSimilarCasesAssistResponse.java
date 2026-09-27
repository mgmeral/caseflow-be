package com.caseflow.ai.api.dto;

import java.util.List;

/**
 * FE-facing response for the {@code POST /api/tickets/{id}/ai-similar-cases} endpoint.
 */
public record AiSimilarCasesAssistResponse(
        Long ticketId,
        List<SimilarCase> cases,
        AiAssistMetadata metadata
) {
    /**
     * @param ticketId  the similar ticket's id, for linking (only tickets the caller may read are returned)
     * @param resolutionSummary snippet of the indexed ticket text
     */
    public record SimilarCase(
            Long ticketId,
            String ticketNo,
            String subject,
            float similarityScore,
            String resolutionSummary,
            List<String> tags
    ) {}

    public static AiSimilarCasesAssistResponse unavailable(Long ticketId, String correlationId, String reason) {
        return new AiSimilarCasesAssistResponse(ticketId, List.of(),
                AiAssistMetadata.unavailable(correlationId, reason));
    }
}
