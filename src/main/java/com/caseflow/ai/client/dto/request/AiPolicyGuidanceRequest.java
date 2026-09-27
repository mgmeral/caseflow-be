package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service's {@code POST /api/ai/tickets/{ticketId}/policy-guidance}.
 *
 * <p>Mirrors {@code caseflow-ai-service}'s {@code PolicyGuidanceRequest}; {@code query},
 * {@code ticketStatus} and {@code priority} are required there.
 */
public record AiPolicyGuidanceRequest(
        String correlationId,
        String query,
        String customerName,
        String ticketStatus,
        String priority,
        List<String> tags,
        Integer topK
) {}
