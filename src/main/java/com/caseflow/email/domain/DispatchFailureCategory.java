package com.caseflow.email.domain;

/**
 * Categorised reason why an outbound email dispatch failed.
 *
 * <p>Stored in {@link OutboundEmailDispatch#failureCategory} for structured diagnostics.
 * Raw detail is preserved in {@code failureReason}.
 */
public enum DispatchFailureCategory {
    /** SMTP authentication rejected (bad credentials or account locked). */
    SMTP_AUTH_FAILURE,
    /** TLS/SSL handshake failed or TCP connection refused. */
    TLS_FAILURE,
    /** From or To address is syntactically or semantically invalid. */
    INVALID_ADDRESS,
    /** Mailbox record is inactive or missing required SMTP configuration. */
    MAILBOX_INACTIVE,
    /** No SMTP sender is configured at all (global sender absent and no mailbox config). */
    UNCONFIGURED,
    /** Catch-all for failures that do not fit the above categories. */
    UNKNOWN
}
