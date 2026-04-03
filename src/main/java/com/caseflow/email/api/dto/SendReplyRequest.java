package com.caseflow.email.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/tickets/{id}/email/reply.
 *
 * <h2>Reply-target resolution</h2>
 * The backend resolves the actual recipient via {@code sourceEventId} when provided:
 * <ol>
 *   <li>Load the source ingress event.</li>
 *   <li>Use {@code replyTo} header if present, otherwise {@code from} header.</li>
 * </ol>
 * Providing {@code toAddress} without {@code sourceEventId} is supported as a manual override
 * but discouraged for normal replies — the backend cannot verify the address matches the
 * actual customer without the source event context.
 *
 * <p>At least one of {@code sourceEventId} or {@code toAddress} must be non-null.
 */
public record SendReplyRequest(

        @NotNull
        Long mailboxId,

        /**
         * ID of the {@code EmailIngressEvent} being replied to.
         * When set, the backend derives the reply-to address from the event headers
         * (replyTo → from). Preferred over a FE-provided {@code toAddress}.
         */
        Long sourceEventId,

        /**
         * Explicit recipient address. Used only when {@code sourceEventId} is null
         * (e.g. proactive outreach, not a reply to an inbound email).
         * Must be a valid email address.
         */
        @Email
        String toAddress,

        @NotBlank
        String subject,

        String textBody,

        String htmlBody,

        /** messageId of the customer email being replied to — used for In-Reply-To threading. */
        String inReplyToMessageId
) {}
