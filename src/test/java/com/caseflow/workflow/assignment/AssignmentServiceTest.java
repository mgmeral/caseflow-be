package com.caseflow.workflow.assignment;

import com.caseflow.common.exception.ActiveAssignmentAlreadyExistsException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.domain.Assignment;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.repository.AssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketHistoryService ticketHistoryService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AssignmentService assignmentService;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setTicketNo("TKT-001");
        ticket.setSubject("Test");
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setAssignedGroupId(20L);
    }

    @Test
    void assign_createsAssignment_whenNoActiveExists() {
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Assignment saved = new Assignment();
        saved.setTicketId(1L);
        saved.setAssignedUserId(10L);
        saved.setAssignedGroupId(20L);
        when(assignmentRepository.save(any())).thenReturn(saved);
        when(ticketRepository.save(any())).thenReturn(ticket);

        Assignment result = assignmentService.assign(1L, 10L, 20L, 5L);

        assertThat(result.getTicketId()).isEqualTo(1L);
        verify(ticketHistoryService).recordAssigned(eq(1L), eq(5L), eq(10L), eq(20L));
    }

    @Test
    void assign_throwsConflict_whenActiveAssignmentExists() {
        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> assignmentService.assign(1L, 10L, 20L, 5L))
                .isInstanceOf(ActiveAssignmentAlreadyExistsException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_throwsTicketNotFound_whenTicketMissing() {
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(99L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.assign(99L, 1L, 1L, 1L))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void unassign_closesActiveAssignmentAndClearsUserButPreservesGroup() {
        ticket.setAssignedUserId(10L);
        ticket.setAssignedGroupId(20L);

        Assignment active = new Assignment();
        active.setTicketId(1L);
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(active));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenReturn(active);
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.unassign(1L, 5L);

        assertThat(active.getUnassignedAt()).isNotNull();
        assertThat(ticket.getAssignedUserId()).isNull();
        // Group is intentionally preserved — ticket stays visible in group queue
        assertThat(ticket.getAssignedGroupId()).isEqualTo(20L);
        verify(ticketHistoryService).recordUnassigned(eq(1L), eq(5L));
    }

    @Test
    void reassign_closesOldAndCreatesNew() {
        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);

        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Assignment newAssignment = new Assignment();
        newAssignment.setTicketId(1L);
        newAssignment.setAssignedUserId(20L);
        when(assignmentRepository.save(any())).thenReturn(existing).thenReturn(newAssignment);
        when(ticketRepository.save(any())).thenReturn(ticket);

        Assignment result = assignmentService.reassign(1L, 20L, 30L, 5L);

        assertThat(existing.getUnassignedAt()).isNotNull();
        verify(ticketHistoryService).recordReassigned(eq(1L), eq(5L), eq(20L), eq(30L));
    }

    @Test
    void getActiveAssignment_returnsEmpty_whenNoneActive() {
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());

        Optional<Assignment> result = assignmentService.getActiveAssignment(1L);
        assertThat(result).isEmpty();
    }

    // ── Group preservation ────────────────────────────────────────────────────

    @Test
    void assign_doesNotOverwriteExistingGroup_whenGroupIdIsNull() {
        ticket.setStatus(TicketStatus.NEW);
        ticket.setAssignedGroupId(99L); // pre-existing group

        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        // Assign only a user, no groupId provided (null)
        assignmentService.assign(1L, 10L, null, 5L);

        // Existing group must be preserved
        assertThat(ticket.getAssignedGroupId()).isEqualTo(99L);
    }

    @Test
    void reassign_doesNotOverwriteExistingGroup_whenGroupIdIsNull() {
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setAssignedGroupId(99L);

        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.reassign(1L, 20L, null, 5L);

        assertThat(ticket.getAssignedGroupId()).isEqualTo(99L);
    }

    // ── Auto-ASSIGNED status transition ───────────────────────────────────────

    @Test
    void assign_autoTransitionsToAssigned_whenTicketIsNew() {
        ticket.setStatus(TicketStatus.NEW);

        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.assign(1L, 10L, 20L, 5L);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void assign_autoTransitionsToAssigned_whenTicketIsTriaged() {
        ticket.setStatus(TicketStatus.TRIAGED);

        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.assign(1L, 10L, 20L, 5L);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void assign_autoTransitionsToAssigned_whenTicketIsReopened() {
        ticket.setStatus(TicketStatus.REOPENED);

        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.assign(1L, 10L, 20L, 5L);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
    }

    @Test
    void assign_doesNotChangeStatus_whenTicketAlreadyInProgress() {
        ticket.setStatus(TicketStatus.IN_PROGRESS);

        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.assign(1L, 10L, 20L, 5L);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }
}
