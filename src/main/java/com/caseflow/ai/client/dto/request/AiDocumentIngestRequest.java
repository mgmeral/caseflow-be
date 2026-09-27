package com.caseflow.ai.client.dto.request;

/**
 * Request sent to the AI service's {@code POST /api/ai/ingest/documents}.
 *
 * <p>Mirrors {@code caseflow-ai-service}'s {@code DocumentIngestRequest}; {@code sourceId},
 * {@code sourceType}, {@code title} and {@code text} are required there. A null
 * {@code customerId} is indexed as {@code GLOBAL} (visible for every customer).
 */
public record AiDocumentIngestRequest(
        String sourceId,
        String sourceType,
        String title,
        String text,
        String customerId
) {}
