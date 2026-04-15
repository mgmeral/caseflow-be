package com.caseflow.sla.domain;

/** Determines how a SLA policy is matched to a ticket. */
public enum SlaScope {
    /** Applies to all tickets unless a more specific policy matches. */
    GLOBAL,
    /** Applies to tickets of a specific {@link com.caseflow.ticket.domain.TicketPriority}. */
    PRIORITY,
    /** Applies to tickets assigned to a specific group. Reserved for future use. */
    GROUP,
    /** Applies to tickets for a specific customer. Reserved for future use. */
    CUSTOMER
}
