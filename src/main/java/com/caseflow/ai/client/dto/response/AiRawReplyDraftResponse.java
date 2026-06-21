package com.caseflow.ai.client.dto.response;

import java.util.List;

/**
 * Raw response from the AI service's reply-draft endpoint.
 * Mapped to the FE-facing DTO by {@link com.caseflow.ai.service.AiAssistService}.
 *
 * <p>Note: the AI service uses {@code suggestedBody} (not {@code draft}) and
 * {@code tone} (not {@code toneApplied}). The service layer maps these to the
 * stable FE-facing field names.
 */
public record AiRawReplyDraftResponse(
        String suggestedBody,
        String tone,
        List<String> warnings,
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId
) {}
