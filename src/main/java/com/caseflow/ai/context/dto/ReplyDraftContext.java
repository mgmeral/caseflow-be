package com.caseflow.ai.context.dto;

import java.util.List;

/**
 * Context assembled for a reply-draft AI request.
 */
public record ReplyDraftContext(
        String ticketNo,
        String subject,
        String status,
        String priority,
        String customerName,
        String latestInboundMessage,
        String latestInboundFrom,
        List<MessageSnippet> threadContext,
        List<String> tags,
        List<String> internalNotes,
        String locale,
        String toneHint
) {
    public record MessageSnippet(String direction, String preview, String sentAt) {}
}
