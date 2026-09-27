package com.caseflow.ai.client.dto.response;

/**
 * Raw response from the AI service's ingest endpoints. {@code status} is {@code SUCCESS},
 * {@code SKIPPED} (nothing to index) or {@code FAILED}.
 */
public record AiIngestResponse(
        String jobId,
        String sourceId,
        int chunksIndexed,
        String status,
        String message
) {}
