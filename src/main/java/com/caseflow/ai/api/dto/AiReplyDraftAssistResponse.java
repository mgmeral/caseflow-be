package com.caseflow.ai.api.dto;

/**
 * FE-facing response for the {@code POST /api/tickets/{id}/ai-reply-draft} endpoint.
 *
 * <p>The {@code draft} field is suggestion-only. The agent must review and approve
 * before sending. CaseFlow BE never auto-sends an AI-generated draft.
 */
public record AiReplyDraftAssistResponse(
        Long ticketId,
        /** Suggested reply text — agent must review before use. */
        String draft,
        String toneApplied,
        AiAssistMetadata metadata
) {
    public static AiReplyDraftAssistResponse unavailable(Long ticketId, String correlationId, String reason) {
        return new AiReplyDraftAssistResponse(ticketId, null, null,
                AiAssistMetadata.unavailable(correlationId, reason));
    }
}
