package com.caseflow.ai.client.dto.response;

/**
 * Raw response from the AI service's summary endpoint.
 * Mapped to the FE-facing DTO by {@link com.caseflow.ai.service.AiAssistService}.
 */
public record AiRawSummaryResponse(
        String summary,
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId
) {}
