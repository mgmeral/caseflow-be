package com.caseflow.workflow.transfer;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.domain.Transfer;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.repository.TransferRepository;
import com.caseflow.workflow.transfer.dto.TransferResponse;
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
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public TransferService(TransferRepository transferRepository,
                           TicketRepository ticketRepository,
                           TicketHistoryService ticketHistoryService,
                           ApplicationEventPublisher eventPublisher,
                           GroupRepository groupRepository,
                           UserRepository userRepository) {
        this.transferRepository = transferRepository;
        this.ticketRepository = ticketRepository;
        this.ticketHistoryService = ticketHistoryService;
        this.eventPublisher = eventPublisher;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    /**
     * Transfers the ticket to the target group.
     * Always clears the current assignee and moves status to TRIAGED so the receiving
     * group can triage and re-assign from a clean state.
     * The {@code clearAssignee} parameter is accepted for backwards compatibility but ignored —
     * assignee is always cleared on transfer.
     */
    @Transactional
    public TransferResponse transfer(Long ticketId, Long fromGroupId, Long toGroupId,
                                     Long transferredBy, String reason, boolean clearAssignee) {
        log.info("Transferring ticket {} — fromGroupId: {}, toGroupId: {}, transferredBy: {}",
                ticketId, fromGroupId, toGroupId, transferredBy);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        ticket.setAssignedGroupId(toGroupId);
        ticket.setAssignedUserId(null);
        ticket.setStatus(TicketStatus.TRIAGED);
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

        String fromGroupName = groupRepository.findById(fromGroupId).map(g -> g.getName()).orElse(null);
        String toGroupName   = groupRepository.findById(toGroupId).map(g -> g.getName()).orElse(null);
        String transferredByName = userRepository.findById(transferredBy).map(u -> u.getUsername()).orElse(null);

        return new TransferResponse(
                saved.getId(), ticketId, fromGroupId, toGroupId,
                fromGroupName, toGroupName, transferredBy, transferredByName,
                saved.getTransferredAt(), reason);
    }

    @Transactional(readOnly = true)
    public List<Transfer> getTransferHistory(Long ticketId) {
        return transferRepository.findByTicketIdOrderByTransferredAtAsc(ticketId);
    }
}
