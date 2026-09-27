package com.caseflow.ai.service;

import com.caseflow.ticket.domain.TicketEventType;
import com.caseflow.ticket.repository.TicketRepository;
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
 * Requests a similar-case index sync when a ticket change affects what is (or should be) indexed.
 *
 * <ul>
 *   <li>Status changes always sync: resolving/closing indexes the ticket, reopening removes it.</li>
 *   <li>Content changes (emails, notes, tags) sync only while the ticket is RESOLVED/CLOSED,
 *       so the indexed resolution stays current.</li>
 * </ul>
 *
 * <p>Runs after commit, in its own transaction, and never throws — a failure here must not
 * affect the ticket action; {@code POST /api/admin/ai/reindex} repairs any gap.
 */
@Component
public class AiTicketSyncListener {

    private static final Logger log = LoggerFactory.getLogger(AiTicketSyncListener.class);

    static final Set<String> STATUS_ACTIONS = Set.of(TicketEventType.STATUS_CHANGED, "CLOSED", "REOPENED");

    private final AiTicketSyncService syncService;
    private final TicketRepository ticketRepository;

    public AiTicketSyncListener(AiTicketSyncService syncService, TicketRepository ticketRepository) {
        this.syncService = syncService;
        this.ticketRepository = ticketRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketHistoryRecorded(TicketHistoryRecordedEvent event) {
        if (event.ticketId() == null) return;
        boolean statusChange = STATUS_ACTIONS.contains(event.actionType());
        if (!statusChange && !AiCacheInvalidationListener.AI_RELEVANT_ACTIONS.contains(event.actionType())) {
            return;
        }
        try {
            ticketRepository.findById(event.ticketId()).ifPresent(ticket -> {
                if (statusChange || AiTicketSyncService.INDEXED_STATUSES.contains(ticket.getStatus())) {
                    syncService.requestSync(ticket, "EVENT");
                }
            });
        } catch (Exception ex) {
            log.warn("AI ticket sync request failed [ticketId={}, action={}]: {}",
                    event.ticketId(), event.actionType(), ex.getMessage());
        }
    }
}
