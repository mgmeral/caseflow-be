package com.caseflow.workflow.history;

/**
 * Published by {@link TicketHistoryService} for every history entry it writes, inside the
 * originating transaction. Listeners that must only react to committed changes should use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.
 *
 * @param ticketId   the ticket the entry belongs to
 * @param actionType one of the {@link com.caseflow.ticket.domain.TicketEventType} codes
 *                   (or an ad-hoc code such as {@code CLOSED}/{@code REOPENED})
 */
public record TicketHistoryRecordedEvent(Long ticketId, String actionType) {}
