package com.caseflow.ai.api.dto;

import java.util.List;

/**
 * FE-facing response for the {@code POST /api/tickets/{id}/ai-policy-guidance} endpoint.
 */
public record AiPolicyGuidanceAssistResponse(
        Long ticketId,
        String guidance,
        List<PolicyCitation> citations,
        AiAssistMetadata metadata
) {
    public record PolicyCitation(
            String policyId,
            String title,
            String excerpt,
            float relevanceScore
    ) {}

    public static AiPolicyGuidanceAssistResponse unavailable(Long ticketId, String correlationId, String reason) {
        return new AiPolicyGuidanceAssistResponse(ticketId, null, List.of(),
                AiAssistMetadata.unavailable(correlationId, reason));
    }
}
