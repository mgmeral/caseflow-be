package com.caseflow.ai.api.dto;

/**
 * Metadata accompanying every AI assist response.
 * Enables FE to display provenance info and handle stale/unavailable states gracefully.
 */
public record AiAssistMetadata(
        String model,
        String promptVersion,
        String generatedAt,
        String correlationId,
        boolean available,
        String unavailableReason
) {
    public static AiAssistMetadata unavailable(String correlationId, String reason) {
        return new AiAssistMetadata(null, null, null, correlationId, false, reason);
    }

    public static AiAssistMetadata of(String model, String promptVersion,
                                       String generatedAt, String correlationId) {
        return new AiAssistMetadata(model, promptVersion, generatedAt, correlationId, true, null);
    }
}
