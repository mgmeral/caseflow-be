package com.caseflow.ai.api.dto;

import java.util.List;

/**
 * FE-facing response for the {@code POST /api/tickets/{id}/ai-summary} endpoint.
 *
 * <p>When {@code metadata.available=false} the summary field is null and warnings is empty.
 * The FE must check {@code metadata.available} before rendering the summary card.
 */
public record AiSummaryAssistResponse(
        Long ticketId,
        String summary,
        List<String> warnings,
        AiAssistMetadata metadata
) {
    public static AiSummaryAssistResponse unavailable(Long ticketId, String correlationId, String reason) {
        return new AiSummaryAssistResponse(ticketId, null, List.of(),
                AiAssistMetadata.unavailable(correlationId, reason));
    }
}
