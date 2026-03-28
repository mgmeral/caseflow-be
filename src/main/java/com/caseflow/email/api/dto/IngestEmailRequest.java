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
        Instant receivedAt,

        /** Optional: ID of the CaseFlow mailbox this email was received on. */
        Long mailboxId,

        /** Optional: the actual SMTP envelope recipient (may differ from To: header). */
        String envelopeRecipient
) {}
