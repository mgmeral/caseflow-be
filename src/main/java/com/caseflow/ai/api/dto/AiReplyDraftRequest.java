package com.caseflow.ai.api.dto;

/**
 * Optional request body for {@code POST /api/tickets/{id}/ai-reply-draft}.
 * All fields are optional — the context builder will populate defaults from ticket data.
 */
public record AiReplyDraftRequest(
        /** Optional tone hint: e.g. "professional", "empathetic", "concise". */
        String toneHint
) {}
