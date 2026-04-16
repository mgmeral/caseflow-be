package com.caseflow.ai.client.dto.response;

/**
 * Raw response from the AI service's reply-draft endpoint.
 */
public record AiRawReplyDraftResponse(
        String draft,
        String toneApplied,
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId
) {}
