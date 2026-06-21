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
        String toneHint,
        /** Active policy/SOP snippets relevant to this ticket (from tags/category). */
        List<String> policySnippets,
        /** Hard constraints the reply must respect, e.g. SLA deadline or plan limits. */
        List<String> constraints,
        /** Code of the mail template to pre-fill; null if none selected. */
        String selectedTemplateCode
) {
    public record MessageSnippet(String direction, String preview, String sentAt) {}
}
