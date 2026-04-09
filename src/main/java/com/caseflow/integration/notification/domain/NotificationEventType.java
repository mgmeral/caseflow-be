package com.caseflow.integration.notification.domain;

/**
 * Ticket lifecycle events that can trigger external channel notifications.
 *
 * <p>Only values declared here are accepted on channel config creation/update.
 * The {@link #supportedValues()} method exposes the canonical set for FE consumption
 * via the event catalog endpoint.
 *
 * <p>Groups:
 * <ul>
 *   <li>Ticket lifecycle — TICKET_CREATED, TICKET_REOPENED, TICKET_RESOLVED, TICKET_CLOSED</li>
 *   <li>Ownership / workflow — TICKET_ASSIGNED, TICKET_UNASSIGNED, TICKET_TRANSFERRED,
 *       ASSIGNEE_CHANGED, GROUP_CHANGED</li>
 *   <li>Priority / SLA — PRIORITY_CHANGED, SLA_WARNING, SLA_BREACHED, SLA_RECOVERED</li>
 *   <li>Customer communication — CUSTOMER_REPLIED, OUTBOUND_EMAIL_FAILED,
 *       SCHEDULED_EMAIL_SENT, SCHEDULED_EMAIL_FAILED</li>
 *   <li>Collaboration / internal — INTERNAL_NOTE_ADDED, USER_MENTIONED_IN_NOTE</li>
 *   <li>Integration / delivery — JIRA_ISSUE_CREATED, JIRA_SYNC_FAILED, WEBHOOK_DELIVERY_FAILED</li>
 * </ul>
 */
public enum NotificationEventType {

    // ── Ticket lifecycle ──────────────────────────────────────────────────────
    TICKET_CREATED,
    TICKET_REOPENED,
    TICKET_RESOLVED,
    TICKET_CLOSED,

    // ── Ownership / workflow ─────────────────────────────────────────────────
    TICKET_ASSIGNED,
    TICKET_UNASSIGNED,
    TICKET_TRANSFERRED,
    ASSIGNEE_CHANGED,
    GROUP_CHANGED,

    // ── Priority / SLA ───────────────────────────────────────────────────────
    PRIORITY_CHANGED,
    SLA_WARNING,
    SLA_BREACHED,
    SLA_RECOVERED,

    // ── Customer communication ───────────────────────────────────────────────
    CUSTOMER_REPLIED,
    /** @deprecated Use {@link #OUTBOUND_EMAIL_FAILED} — kept for backward compatibility with existing channel configs. */
    @Deprecated
    OUTBOUND_REPLY_FAILED,
    OUTBOUND_EMAIL_FAILED,
    SCHEDULED_EMAIL_SENT,
    SCHEDULED_EMAIL_FAILED,

    // ── Collaboration / internal ─────────────────────────────────────────────
    INTERNAL_NOTE_ADDED,
    USER_MENTIONED_IN_NOTE,

    // ── Integration / delivery ───────────────────────────────────────────────
    JIRA_ISSUE_CREATED,
    JIRA_SYNC_FAILED,
    WEBHOOK_DELIVERY_FAILED;

    /**
     * Returns all supported event values as their string names.
     * Used by the event catalog endpoint so the FE can enumerate valid options
     * without coupling to the Java enum directly.
     */
    public static java.util.List<String> supportedValues() {
        return java.util.Arrays.stream(values())
                .map(Enum::name)
                .toList();
    }
}
