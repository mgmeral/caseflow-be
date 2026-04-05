package com.caseflow.email.api.dto;

import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;

import java.time.Instant;
import java.util.List;

public record IngressEventResponse(
        Long id,
        Long mailboxId,
        String messageId,
        String rawFrom,
        String rawSubject,
        String inReplyTo,
        String rawReplyTo,
        Instant receivedAt,
        IngressEventStatus status,
        String failureReason,
        Integer processingAttempts,
        Instant lastAttemptAt,
        Instant processedAt,
        String documentId,
        Long ticketId,
        /**
         * Attachment metadata for this inbound email, sourced from the attachment_metadata
         * table. Populated in the detail endpoint; empty list on list/thread endpoints.
         * Never null.
         */
        List<AttachmentMetadataResponse> attachments
) {}
