package com.caseflow.ticket.domain;

/**
 * Canonical event type codes used in {@link History#actionType}.
 *
 * <p>These constants are stored as plain strings in the database so that
 * the column remains queryable without full enum serialisation.
 */
public final class TicketEventType {

    // ── Ticket lifecycle ──────────────────────────────────────────────────────
    public static final String TICKET_CREATED       = "TICKET_CREATED";
    public static final String STATUS_CHANGED       = "STATUS_CHANGED";
    public static final String PRIORITY_CHANGED     = "PRIORITY_CHANGED";
    public static final String ASSIGNED_TO_GROUP    = "ASSIGNED_TO_GROUP";
    public static final String ASSIGNED_TO_USER     = "ASSIGNED_TO_USER";
    public static final String TRANSFERRED          = "TRANSFERRED";

    // ── Email activity ────────────────────────────────────────────────────────
    public static final String INBOUND_EMAIL_RECEIVED  = "INBOUND_EMAIL_RECEIVED";
    public static final String OUTBOUND_REPLY_QUEUED   = "OUTBOUND_REPLY_QUEUED";
    public static final String OUTBOUND_REPLY_SENT     = "OUTBOUND_REPLY_SENT";
    public static final String OUTBOUND_REPLY_FAILED   = "OUTBOUND_REPLY_FAILED";

    // ── Content ───────────────────────────────────────────────────────────────
    public static final String INTERNAL_NOTE_ADDED = "INTERNAL_NOTE_ADDED";
    public static final String ATTACHMENT_ADDED    = "ATTACHMENT_ADDED";
    public static final String TEMPLATE_USED       = "TEMPLATE_USED";

    private TicketEventType() {}
}
