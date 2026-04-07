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

    // ── Tag events ────────────────────────────────────────────────────────────
    public static final String TAG_ADDED   = "TAG_ADDED";
    public static final String TAG_REMOVED = "TAG_REMOVED";

    // ── Jira integration ──────────────────────────────────────────────────────
    public static final String JIRA_ISSUE_CREATE_REQUESTED = "JIRA_ISSUE_CREATE_REQUESTED";
    public static final String JIRA_ISSUE_CREATED          = "JIRA_ISSUE_CREATED";
    public static final String JIRA_ISSUE_CREATE_FAILED    = "JIRA_ISSUE_CREATE_FAILED";

    // ── External notifications ────────────────────────────────────────────────
    public static final String EXTERNAL_NOTIFICATION_QUEUED  = "EXTERNAL_NOTIFICATION_QUEUED";
    public static final String EXTERNAL_NOTIFICATION_SENT    = "EXTERNAL_NOTIFICATION_SENT";
    public static final String EXTERNAL_NOTIFICATION_FAILED  = "EXTERNAL_NOTIFICATION_FAILED";

    // ── Scheduled email ───────────────────────────────────────────────────────
    public static final String SCHEDULED_EMAIL_CREATED  = "SCHEDULED_EMAIL_CREATED";
    public static final String SCHEDULED_EMAIL_CANCELED = "SCHEDULED_EMAIL_CANCELED";
    public static final String SCHEDULED_EMAIL_SENT     = "SCHEDULED_EMAIL_SENT";
    public static final String SCHEDULED_EMAIL_FAILED   = "SCHEDULED_EMAIL_FAILED";

    private TicketEventType() {}
}
