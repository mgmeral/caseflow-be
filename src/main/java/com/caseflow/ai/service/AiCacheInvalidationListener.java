package com.caseflow.ai.service;

import com.caseflow.ticket.domain.TicketEventType;
import com.caseflow.workflow.history.TicketHistoryRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * Invalidates cached AI responses when a ticket change affects what the AI was given.
 *
 * <p>Summary and reply-draft requests include messages, internal notes, tags, status and
 * priority, so any of those changing makes a cached response stale. Assignment, transfer,
 * Jira and notification events do not.
 *
 * <p>Runs after the originating transaction commits, in its own transaction, and never
 * throws — a failed invalidation only leaves the 30-minute cache TTL as the fallback and
 * must not affect the ticket action that triggered it.
 *
 * <p>Known gap: editing only a ticket's subject/description writes no history entry, so it
 * does not invalidate the cache.
 */
@Component
public class AiCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(AiCacheInvalidationListener.class);

    static final Set<String> AI_RELEVANT_ACTIONS = Set.of(
            TicketEventType.STATUS_CHANGED,
            TicketEventType.PRIORITY_CHANGED,
            "CLOSED",
            "REOPENED",
            TicketEventType.INTERNAL_NOTE_ADDED,
            TicketEventType.INBOUND_EMAIL_RECEIVED,
            TicketEventType.OUTBOUND_REPLY_SENT,
            TicketEventType.SCHEDULED_EMAIL_SENT,
            TicketEventType.TAG_ADDED,
            TicketEventType.TAG_REMOVED
    );

    private final AiSourceVersionService sourceVersionService;

    public AiCacheInvalidationListener(AiSourceVersionService sourceVersionService) {
        this.sourceVersionService = sourceVersionService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketHistoryRecorded(TicketHistoryRecordedEvent event) {
        if (event.ticketId() == null || !AI_RELEVANT_ACTIONS.contains(event.actionType())) {
            return;
        }
        try {
            sourceVersionService.onAiRelevantChange(event.ticketId());
        } catch (Exception ex) {
            log.warn("AI cache invalidation failed [ticketId={}, action={}]: {}",
                    event.ticketId(), event.actionType(), ex.getMessage());
        }
    }
}
