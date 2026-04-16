package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service for policy guidance retrieval.
 */
public record AiPolicyGuidanceRequest(
        String correlationId,
        String ticketNo,
        String userQuestion,
        String subject,
        String status,
        List<String> tags,
        String customerName,
        String locale,
        List<String> scopeHints
) {}
