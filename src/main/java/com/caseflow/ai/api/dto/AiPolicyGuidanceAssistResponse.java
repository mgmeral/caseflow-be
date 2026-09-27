package com.caseflow.ai.api.dto;

import java.util.List;

/**
 * FE-facing response for the {@code POST /api/tickets/{id}/ai-policy-guidance} endpoint.
 *
 * <p>An empty {@code citations} list with {@code metadata.available == true} means no policy
 * document matched — {@code guidance} is then an explanatory message, not policy advice.
 */
public record AiPolicyGuidanceAssistResponse(
        Long ticketId,
        String guidance,
        List<PolicyCitation> citations,
        List<String> recommendedActions,
        Double confidence,
        AiAssistMetadata metadata
) {
    public record PolicyCitation(
            String policyId,
            String title,
            String excerpt,
            float relevanceScore
    ) {}

    public static AiPolicyGuidanceAssistResponse unavailable(Long ticketId, String correlationId, String reason) {
        return new AiPolicyGuidanceAssistResponse(ticketId, null, List.of(), List.of(), null,
                AiAssistMetadata.unavailable(correlationId, reason));
    }
}
