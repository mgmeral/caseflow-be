package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service for ticket summarization.
 *
 * <p>Downstream path: {@code POST /api/ai/tickets/{ticketId}/summary}
 * The ticketId is passed as a path variable by {@link com.caseflow.ai.client.CaseflowAiClient},
 * not repeated in this body.
 */
public record AiSummaryRequest(
        String correlationId,
        String customerName,
        String ticketStatus,
        String priority,
        String slaState,
        List<String> tags,
        List<LatestMessage> latestMessages,
        List<String> internalNotes,
        String locale,
        String summaryStyle
) {
    /**
     * A single message entry in the unified conversation timeline.
     * Direction is {@code "inbound"} or {@code "outbound"}.
     */
    public record LatestMessage(String direction, String from, String preview, String sentAt) {}
}
