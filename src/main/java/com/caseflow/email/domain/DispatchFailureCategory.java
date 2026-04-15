package com.caseflow.email.domain;

/**
 * Categorised reason why an outbound email dispatch failed.
 *
 * <p>Stored in {@link OutboundEmailDispatch#failureCategory} for structured diagnostics.
 * Raw detail is preserved in {@code failureReason}.
 */
public enum DispatchFailureCategory {
    // ── Connection / transport ────────────────────────────────────────────────

    /** TCP connection to SMTP host refused or timed out. */
    SMTP_CONNECTION,
    /** SMTP authentication rejected (bad credentials or account locked). */
    SMTP_AUTH_FAILURE,
    /** TLS/SSL handshake failed. */
    TLS_FAILURE,

    // ── Server-side rejection ─────────────────────────────────────────────────

    /** Server temporarily throttled the sending rate (421/450 transient limit). */
    SMTP_RATE_LIMITED,
    /** Server rejected the recipient address (550/551 — unknown or blocked). */
    RECIPIENT_REJECTED,
    /** Recipient mailbox is over quota (452). */
    MAILBOX_FULL,
    /** Server rejected the message content (554 / spam/policy filter hit). */
    CONTENT_REJECTED,

    // ── Config / pre-send ─────────────────────────────────────────────────────

    /** From or To address is syntactically or semantically invalid. */
    INVALID_ADDRESS,
    /** Mailbox record is inactive or missing required SMTP configuration. */
    MAILBOX_INACTIVE,
    /** No SMTP sender is configured at all (global sender absent and no mailbox config). */
    UNCONFIGURED,
    /** Pre-send revalidation failed (ticket closed, dispatch canceled, etc.). */
    REVALIDATION_FAILURE,

    /** Catch-all for failures that do not fit the above categories. */
    UNKNOWN
}
