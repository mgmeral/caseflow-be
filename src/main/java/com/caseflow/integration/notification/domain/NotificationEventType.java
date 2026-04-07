package com.caseflow.integration.notification.domain;

/**
 * Ticket lifecycle events that can trigger external channel notifications.
 */
public enum NotificationEventType {
    TICKET_CREATED,
    TICKET_ASSIGNED,
    TICKET_TRANSFERRED,
    TICKET_RESOLVED,
    TICKET_CLOSED,
    OUTBOUND_REPLY_FAILED
}
