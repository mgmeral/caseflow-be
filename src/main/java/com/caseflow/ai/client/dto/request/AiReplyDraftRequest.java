package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service for reply draft generation.
 *
 * <p>Downstream path: {@code POST /api/ai/tickets/{ticketId}/reply-draft}
 * The ticketId is passed as a path variable by {@link com.caseflow.ai.client.CaseflowAiClient},
 * not repeated in this body.
 */
public record AiReplyDraftRequest(
        String correlationId,
        String customerName,
        String locale,
        String tone,
        String ticketStatus,
        String priority,
        List<String> tags,
        List<LatestMessage> latestMessages,
        List<String> internalNotes,
        List<String> policySnippets,
        List<String> constraints,
        String replyGoal,
        String selectedTemplateCode
) {
    /**
     * A single message entry in the unified conversation timeline.
     * Direction is {@code "inbound"} or {@code "outbound"}.
     */
    public record LatestMessage(String direction, String from, String preview, String sentAt) {}
}
