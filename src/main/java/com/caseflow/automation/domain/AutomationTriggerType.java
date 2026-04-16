package com.caseflow.automation.domain;

/**
 * Events that can trigger an automation rule.
 *
 * <p>Currently stored but not executed — the rule engine is scaffolded for future
 * implementation. Rules with {@code isActive = false} (the default) will never fire.
 */
public enum AutomationTriggerType {
    /** A new ticket was created. */
    TICKET_CREATED,
    /** A ticket's status changed. */
    STATUS_CHANGED,
    /** A tag was added to a ticket. */
    TAG_ADDED,
    /** A ticket was assigned to a user or group. */
    ASSIGNED,
    /** The SLA warning threshold was crossed. */
    SLA_WARNING,
    /** The SLA resolution deadline was breached. */
    SLA_BREACHED,
    /** An inbound email arrived on an existing ticket. */
    INBOUND_EMAIL_RECEIVED,
    /** An outbound reply was sent. */
    OUTBOUND_EMAIL_SENT
}
