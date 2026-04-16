package com.caseflow.ai.context.dto;

import java.util.List;

/**
 * Context assembled for a ticket-summary AI request.
 * Contains only fields relevant to generating a concise summary.
 */
public record SummaryContext(
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
