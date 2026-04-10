package com.caseflow.workflow.transfer;

import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.domain.Transfer;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.repository.TransferRepository;
import com.caseflow.workflow.transfer.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private TransferRepository transferRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketHistoryService ticketHistoryService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GroupRepository groupRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private TransferService transferService;

    private Ticket ticket;
    private Transfer savedTransfer;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setTicketNo("TKT-001");
        ticket.setSubject("Test ticket");
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setAssignedUserId(99L);
        ticket.setAssignedGroupId(1L);

        savedTransfer = new Transfer();
        savedTransfer.setTicketId(10L);
        savedTransfer.setFromGroupId(1L);
        savedTransfer.setToGroupId(2L);
        savedTransfer.setTransferredBy(5L);
        savedTransfer.setReason("Escalation");
    }

    @Test
    void transfer_setsStatusToTriaged() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(transferRepository.save(any())).thenReturn(savedTransfer);
        when(groupRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        transferService.transfer(10L, 1L, 2L, 5L, "Escalation", false);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.TRIAGED);
    }

    @Test
    void transfer_alwaysClearsAssignee() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(transferRepository.save(any())).thenReturn(savedTransfer);
        when(groupRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        // clearAssignee=false — but assignee must still be cleared on transfer
        transferService.transfer(10L, 1L, 2L, 5L, "Escalation", false);

        assertThat(ticket.getAssignedUserId()).isNull();
    }

    @Test
    void transfer_setsToGroup() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(transferRepository.save(any())).thenReturn(savedTransfer);
        when(groupRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        transferService.transfer(10L, 1L, 2L, 5L, "Escalation", false);

        assertThat(ticket.getAssignedGroupId()).isEqualTo(2L);
    }

    @Test
    void transfer_populatesGroupAndUserNames_inResponse() {
        Group fromGroup = new Group();
        fromGroup.setName("Support Team");
        Group toGroup = new Group();
        toGroup.setName("Escalation Team");
        User transferredBy = new User();
        transferredBy.setUsername("agent1");
        transferredBy.setEmail("agent1@example.com");
        transferredBy.setFullName("Agent One");

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(transferRepository.save(any())).thenReturn(savedTransfer);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(fromGroup));
        when(groupRepository.findById(2L)).thenReturn(Optional.of(toGroup));
        when(userRepository.findById(5L)).thenReturn(Optional.of(transferredBy));

        TransferResponse response = transferService.transfer(10L, 1L, 2L, 5L, "Escalation", false);

        assertThat(response.fromGroupName()).isEqualTo("Support Team");
        assertThat(response.toGroupName()).isEqualTo("Escalation Team");
        assertThat(response.transferredByName()).isEqualTo("agent1");
    }

    @Test
    void transfer_toleratesMissingGroupOrUser_inResponse() {
        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticket);
        when(transferRepository.save(any())).thenReturn(savedTransfer);
        when(groupRepository.findById(any())).thenReturn(Optional.empty());
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        TransferResponse response = transferService.transfer(10L, 1L, 2L, 5L, "Escalation", false);

        assertThat(response.fromGroupName()).isNull();
        assertThat(response.toGroupName()).isNull();
        assertThat(response.transferredByName()).isNull();
    }
}
