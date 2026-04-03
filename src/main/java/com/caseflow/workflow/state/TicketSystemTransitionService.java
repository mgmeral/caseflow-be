package com.caseflow.workflow.state;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Applies system-triggered ticket status transitions driven by email events.
 *
 * <p>These transitions are not user-initiated — they are automatic responses
 * to inbound or outbound email activity. All transitions are recorded in ticket history
 * with a null performedBy (system actor).
 */
@Service
public class TicketSystemTransitionService {

    private static final Logger log = LoggerFactory.getLogger(TicketSystemTransitionService.class);

    private final TicketRepository ticketRepository;
    private final TicketStateMachineService stateMachine;
    private final TicketHistoryService historyService;

    public TicketSystemTransitionService(TicketRepository ticketRepository,
                                         TicketStateMachineService stateMachine,
                                         TicketHistoryService historyService) {
        this.ticketRepository = ticketRepository;
        this.stateMachine = stateMachine;
        this.historyService = historyService;
    }

    /**
     * Applies the system transition for a newly received inbound customer email.
     * If the current status triggers an automatic transition (e.g. WAITING_CUSTOMER → IN_PROGRESS),
     * the ticket is updated and a system history event is recorded.
     *
     * @param ticketId  the ticket that received the email
     * @param eventId   the ingress event id (for log correlation)
     */
    @Transactional
    public void applyInboundEmailTransition(Long ticketId, Long eventId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            Optional<TicketStatus> target = stateMachine.systemTransitionOnInbound(ticket.getStatus());
            if (target.isPresent()) {
                applyTransition(ticket, target.get(),
                        "SYSTEM_INBOUND_EMAIL",
                        "eventId=" + eventId + "; from=" + ticket.getStatus() + "; to=" + target.get());
            }
        });
    }

    /**
     * Applies the system transition triggered by an agent sending an outbound reply.
     * Typically transitions active work states → WAITING_CUSTOMER.
     *
     * @param ticketId   the ticket the reply was sent for
     * @param dispatchId the outbound dispatch id (for log correlation)
     */
    @Transactional
    public void applyOutboundReplyTransition(Long ticketId, Long dispatchId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            Optional<TicketStatus> target = stateMachine.systemTransitionOnOutbound(ticket.getStatus());
            if (target.isPresent()) {
                applyTransition(ticket, target.get(),
                        "SYSTEM_OUTBOUND_REPLY",
                        "dispatchId=" + dispatchId + "; from=" + ticket.getStatus() + "; to=" + target.get());
            }
        });
    }

    private void applyTransition(Ticket ticket, TicketStatus newStatus,
                                  String actionType, String detail) {
        TicketStatus previousStatus = ticket.getStatus();
        ticket.setStatus(newStatus);
        if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(Instant.now());
        }
        ticketRepository.save(ticket);
        historyService.record(ticket.getId(), actionType, null, detail);
        log.info("TICKET_STATUS_SYSTEM_TRANSITION ticketId={} {} → {} ({})",
                ticket.getId(), previousStatus, newStatus, actionType);
    }
}
