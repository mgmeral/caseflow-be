package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Response for the mailbox connection test endpoint.
 *
 * <p>{@code success} indicates whether the IMAP connection and folder access succeeded.
 * {@code message} provides an operator-readable summary — on failure it describes the problem
 * without exposing raw credentials or internal stack details.
 */
public record MailboxConnectionTestResponse(
        boolean success,
        String message,
        Instant testedAt
) {}
