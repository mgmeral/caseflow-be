package com.caseflow.ai.client.dto.response;

import java.util.List;

/**
 * Raw response from the AI service's policy-guidance endpoint.
 */
public record AiRawPolicyGuidanceResponse(
        String guidance,
        List<PolicySnippet> citations,
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId
) {
    public record PolicySnippet(
            String policyId,
            String title,
            String excerpt,
            float relevanceScore
    ) {}
}
