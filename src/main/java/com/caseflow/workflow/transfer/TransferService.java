package com.caseflow.workflow.transfer;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.domain.Transfer;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final TicketRepository ticketRepository;
    private final TicketHistoryService ticketHistoryService;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(TransferRepository transferRepository,
                           TicketRepository ticketRepository,
                           TicketHistoryService ticketHistoryService,
                           ApplicationEventPublisher eventPublisher) {
        this.transferRepository = transferRepository;
        this.ticketRepository = ticketRepository;
        this.ticketHistoryService = ticketHistoryService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Transfer transfer(Long ticketId, Long fromGroupId, Long toGroupId,
                             Long transferredBy, String reason, boolean clearAssignee) {
        log.info("Transferring ticket {} — fromGroupId: {}, toGroupId: {}, clearAssignee: {}, transferredBy: {}",
                ticketId, fromGroupId, toGroupId, clearAssignee, transferredBy);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.setAssignedGroupId(toGroupId);
        if (clearAssignee) {
            log.info("Clearing assignee on ticket {} as part of transfer", ticketId);
            ticket.setAssignedUserId(null);
        }
        ticketRepository.save(ticket);

        Transfer transfer = new Transfer();
        transfer.setTicketId(ticketId);
        transfer.setFromGroupId(fromGroupId);
        transfer.setToGroupId(toGroupId);
        transfer.setTransferredBy(transferredBy);
        transfer.setReason(reason);
        Transfer saved = transferRepository.save(transfer);

        ticketHistoryService.recordTransferred(ticketId, transferredBy, fromGroupId, toGroupId);
        eventPublisher.publishEvent(new TicketDomainEvent(ticketId, ticket.getPublicId(),
                NotificationEventType.TICKET_TRANSFERRED, transferredBy,
                ticket.getCustomerId(), ticket.getAssignedGroupId()));
        log.info("Ticket {} transferred — fromGroupId: {} -> toGroupId: {}", ticketId, fromGroupId, toGroupId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Transfer> getTransferHistory(Long ticketId) {
        return transferRepository.findByTicketIdOrderByTransferredAtAsc(ticketId);
    }
}
