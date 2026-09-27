package com.caseflow.ai.client.dto.response;

import java.util.List;

/**
 * Raw response from the AI service's policy-guidance endpoint.
 *
 * <p>Mirrors {@code caseflow-ai-service}'s {@code PolicyGuidanceResponse}. When no policy document
 * is retrieved, the AI service skips the LLM and returns an empty {@code policyReferences} list with
 * a warning — callers must treat that as "no policy found", not as an answer.
 */
public record AiRawPolicyGuidanceResponse(
        String requestId,
        String ticketId,
        String answer,
        List<String> recommendedActions,
        Double confidence,
        List<PolicyReference> policyReferences,
        List<String> warnings,
        String model,
        String promptVersion,
        String generatedAt
) {
    public record PolicyReference(
            String sourceId,
            String title,
            String snippet,
            Double score
    ) {}
}
