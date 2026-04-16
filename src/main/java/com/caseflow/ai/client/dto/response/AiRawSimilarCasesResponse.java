package com.caseflow.ai.client.dto.response;

import java.util.List;

/**
 * Raw response from the AI service's similar-cases endpoint.
 */
public record AiRawSimilarCasesResponse(
        List<SimilarCase> cases,
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId
) {
    public record SimilarCase(
            String ticketNo,
            String subject,
            float similarityScore,
            String resolutionSummary,
            List<String> tags
    ) {}
}
