package com.caseflow.common.domain;

/**
 * How to handle inbound emails from senders not matched to any customer.
 * Shared by the email and customer modules to avoid cross-module dependency.
 */
public enum UnknownSenderPolicy {
    /** Queue for human review — ticket created in quarantine state. */
    MANUAL_REVIEW,
    /** Create a new ticket without a customer link. */
    CREATE_UNMATCHED_TICKET,
    /** Silently discard the email. */
    IGNORE,
    /** Send an automated rejection response and discard. */
    REJECT
}
