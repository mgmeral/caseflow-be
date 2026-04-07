package com.caseflow.integration.domain;

import com.caseflow.integration.notification.domain.NotificationEventType;

import java.util.UUID;

/**
 * Spring ApplicationEvent published when a ticket action occurs that external
 * notification channels may need to react to.
 *
 * <p>Published inside the originating transaction; listeners annotated with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} will only fire
 * after the originating transaction commits successfully.
 */
public class TicketDomainEvent {

    private final Long ticketId;
    private final UUID ticketPublicId;
    private final NotificationEventType eventType;
    private final Long actorId;          // nullable for system events
    private final Long customerId;       // nullable
    private final Long assignedGroupId;  // nullable — used for GROUP-scoped channel matching

    public TicketDomainEvent(Long ticketId, UUID ticketPublicId, NotificationEventType eventType,
                             Long actorId, Long customerId, Long assignedGroupId) {
        this.ticketId = ticketId;
        this.ticketPublicId = ticketPublicId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.customerId = customerId;
        this.assignedGroupId = assignedGroupId;
    }

    public Long getTicketId() { return ticketId; }
    public UUID getTicketPublicId() { return ticketPublicId; }
    public NotificationEventType getEventType() { return eventType; }
    public Long getActorId() { return actorId; }
    public Long getCustomerId() { return customerId; }
    public Long getAssignedGroupId() { return assignedGroupId; }
}
