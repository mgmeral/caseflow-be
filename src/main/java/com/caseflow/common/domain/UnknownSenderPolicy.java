package com.caseflow.common.domain;

/**
 * How to handle inbound emails from senders not matched to any customer.
 * Shared by the email and customer modules to avoid cross-module dependency.
 *
 * <p><strong>Invariant:</strong> none of these policies may create a ticket.
 * Ticket creation is only permitted when an email matches an existing thread
 * ({@code LINK_TO_TICKET}) or a valid customer routing rule ({@code CREATE_TICKET}).
 */
public enum UnknownSenderPolicy {
    /** Hold the ingress event in QUARANTINED state for operator review. No ticket is created. */
    MANUAL_REVIEW,
    /** Silently discard the email. No ticket is created. */
    IGNORE,
    /** Send an automated rejection response and discard. No ticket is created. */
    REJECT
}
