package com.caseflow.ai.api.dto;

import java.util.List;

/**
 * FE-facing response for the {@code POST /api/tickets/{id}/ai-reply-draft} endpoint.
 *
 * <p>The {@code draft} field is suggestion-only. The agent must review and approve
 * before sending. CaseFlow BE never auto-sends an AI-generated draft.
 *
 * <p>When {@code metadata.available=false} draft and toneApplied are null, warnings is empty.
 */
public record AiReplyDraftAssistResponse(
        Long ticketId,
        /** Suggested reply text — agent must review before use. */
        String draft,
        String toneApplied,
        List<String> warnings,
        AiAssistMetadata metadata
) {
    public static AiReplyDraftAssistResponse unavailable(Long ticketId, String correlationId, String reason) {
        return new AiReplyDraftAssistResponse(ticketId, null, null, List.of(),
                AiAssistMetadata.unavailable(correlationId, reason));
    }
}
