package com.caseflow.email.service;

/**
 * Result returned by {@link EmailRoutingService} for a single inbound email.
 * The action tells the ingress pipeline what to do next; execution is delegated
 * to TicketService so email services never create tickets directly.
 */
public record RoutingResult(
        Long customerId,
        Long ticketId,
        Action action,
        String quarantineReason
) {

    public enum Action {
        /** Create a new ticket and link the email to it. */
        CREATE_TICKET,
        /** Link the email to an existing ticket. */
        LINK_TO_TICKET,
        /** Hold for human review — no ticket created yet. */
        QUARANTINE,
        /** Silently discard — do not create or link. */
        IGNORE,
        /** Reject — bounce or flag as unwanted. */
        REJECT
    }

    public static RoutingResult createTicket(Long customerId) {
        return new RoutingResult(customerId, null, Action.CREATE_TICKET, null);
    }

    public static RoutingResult linkToTicket(Long customerId, Long ticketId) {
        return new RoutingResult(customerId, ticketId, Action.LINK_TO_TICKET, null);
    }

    public static RoutingResult quarantine(String reason) {
        return new RoutingResult(null, null, Action.QUARANTINE, reason);
    }

    public static RoutingResult ignore() {
        return new RoutingResult(null, null, Action.IGNORE, null);
    }

    public static RoutingResult reject() {
        return new RoutingResult(null, null, Action.REJECT, null);
    }
}
