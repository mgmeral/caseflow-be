package com.caseflow.ai.client.dto.response;

import java.util.List;

/**
 * Raw response from the AI service's summary endpoint.
 * Mapped to the FE-facing DTO by {@link com.caseflow.ai.service.AiAssistService}.
 */
public record AiRawSummaryResponse(
        String summary,
        List<String> warnings,
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId
) {}
