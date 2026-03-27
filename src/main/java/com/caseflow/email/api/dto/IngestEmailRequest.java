package com.caseflow.email.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Request body for POST /api/emails/ingest.
 * Represents a parsed inbound email to be ingested into CaseFlow.
 */
public record IngestEmailRequest(

        @NotBlank
        String messageId,

        String inReplyTo,

        List<String> references,

        String subject,

        @NotBlank
        String from,

        List<String> to,

        List<String> cc,

        String textBody,

        String htmlBody,

        @NotNull
        Instant receivedAt
) {}
