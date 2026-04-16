package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service for reply draft generation.
 */
public record AiReplyDraftRequest(
        String correlationId,
        String ticketNo,
        String subject,
        String status,
        String priority,
        String customerName,
        /** The latest inbound message content (text). */
        String latestInboundMessage,
        String latestInboundFrom,
        /** Short thread context — last N messages ordered oldest-first. */
        List<MessageSnippet> threadContext,
        List<String> policySnippets,
        String locale,
        /** Tone/goal hint: e.g. "professional", "empathetic", "follow-up". */
        String toneHint
) {
    public record MessageSnippet(String direction, String preview, String sentAt) {}
}
