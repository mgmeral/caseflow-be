package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service for ticket summarization.
 * Contains only the fields relevant to that operation — no raw entity dump.
 */
public record AiSummaryRequest(
        String correlationId,
        String ticketNo,
        String subject,
        String status,
        String priority,
        String customerName,
        String assignedUserName,
        String assignedGroupName,
        List<String> tags,
        String slaState,
        List<MessageSnippet> recentInboundMessages,
        List<MessageSnippet> recentOutboundMessages,
        List<String> recentInternalNotes,
        String locale
) {
    public record MessageSnippet(String from, String preview, String receivedAt) {}
}
