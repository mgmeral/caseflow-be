package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Response for the mailbox SMTP connection test endpoint.
 *
 * <p>Distinct from {@link MailboxConnectionTestResponse} (which tests IMAP).
 * {@code success} indicates whether a TCP connection to the configured SMTP
 * host:port succeeded and no misconfig was detected.
 * {@code message} provides an operator-readable summary without exposing credentials.
 */
public record SmtpConnectionTestResponse(
        boolean success,
        String message,
        Instant testedAt
) {}
