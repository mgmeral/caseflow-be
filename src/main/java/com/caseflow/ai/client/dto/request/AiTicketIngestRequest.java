package com.caseflow.ai.client.dto.request;

import java.util.List;

/**
 * Request sent to the AI service's {@code POST /api/ai/ingest/tickets}.
 *
 * <p>Mirrors {@code caseflow-ai-service}'s {@code TicketIngestRequest}; {@code sourceId},
 * {@code subject}, {@code body} and {@code status} are required there. {@code sourceId} is the
 * ticket's {@code publicId} (ADR-0003). Re-sending the same {@code sourceId} replaces the
 * ticket's indexed chunks.
 */
public record AiTicketIngestRequest(
        String sourceId,
        String customerId,
        String groupId,
        String customerName,
        String subject,
        String body,
        String resolutionSummary,
        List<String> tags,
        String status
) {}
