package com.caseflow.workflow.assignment;

import com.caseflow.common.exception.ActiveAssignmentAlreadyExistsException;
import com.caseflow.common.exception.ActiveAssignmentNotFoundException;
import com.caseflow.common.exception.ReassignTargetSameAsCurrentException;
import com.caseflow.common.exception.ReassignTargetUserInactiveException;
import com.caseflow.common.exception.ReassignTargetUserNotFoundException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
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
import static org.mockito.Mockito.inOrder;
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
    private UserRepository userRepository;

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
        existing.setAssignedGroupId(20L);

        User newUser = activeUser(20L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(newUser));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Assignment newAssignment = new Assignment();
        newAssignment.setTicketId(1L);
        newAssignment.setAssignedUserId(20L);
        when(assignmentRepository.save(any())).thenReturn(existing).thenReturn(newAssignment);
        when(ticketRepository.save(any())).thenReturn(ticket);

        Assignment result = assignmentService.reassign(1L, 20L, 30L, 5L);

        assertThat(existing.getUnassignedAt()).isNotNull();
        // effective group is 30L (explicitly provided)
        verify(ticketHistoryService).recordReassigned(eq(1L), eq(5L), eq(20L), eq(30L));
    }

    @Test
    void reassign_flushesBeforeInsert_toPreventUniqueConstraintViolation() {
        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);
        existing.setAssignedGroupId(20L);

        User newUser = activeUser(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.of(newUser));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.reassign(1L, 99L, null, 5L);

        // flush must be called after save(existing) and before save(newAssignment)
        var order = inOrder(assignmentRepository);
        order.verify(assignmentRepository).save(existing);
        order.verify(assignmentRepository).flush();
        order.verify(assignmentRepository).save(any(Assignment.class));
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
        existing.setAssignedGroupId(99L);

        User newUser = activeUser(20L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(newUser));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        Assignment result = assignmentService.reassign(1L, 20L, null, 5L);

        // Ticket group must be preserved
        assertThat(ticket.getAssignedGroupId()).isEqualTo(99L);
        // Effective group derived from active assignment's group must flow into history
        verify(ticketHistoryService).recordReassigned(eq(1L), eq(5L), eq(20L), eq(99L));
        // New assignment row uses effective group
        assertThat(result.getAssignedGroupId()).isEqualTo(99L);
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

    // ── Reassign guards ───────────────────────────────────────────────────────

    @Test
    void reassign_throwsActiveAssignmentNotFound_whenNoActiveAssignment() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.reassign(1L, 20L, 30L, 5L))
                .isInstanceOf(ActiveAssignmentNotFoundException.class)
                .hasMessageContaining("1");

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void reassign_throwsReassignTargetUserNotFoundException_whenUserNotFound() {
        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);
        existing.setAssignedGroupId(20L);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assignmentService.reassign(1L, 99L, null, 5L))
                .isInstanceOf(ReassignTargetUserNotFoundException.class)
                .hasMessageContaining("99");

        verify(assignmentRepository, never()).flush();
    }

    @Test
    void reassign_throwsReassignTargetUserInactiveException_whenUserInactive() {
        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);
        existing.setAssignedGroupId(20L);

        User inactive = new User();
        inactive.setIsActive(false);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(55L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> assignmentService.reassign(1L, 55L, null, 5L))
                .isInstanceOf(ReassignTargetUserInactiveException.class)
                .hasMessageContaining("55");

        verify(assignmentRepository, never()).flush();
    }

    @Test
    void reassign_throwsSameAsCurrentException_whenTargetIdenticalToCurrentAssignment() {
        ticket.setAssignedGroupId(20L);

        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);
        existing.setAssignedGroupId(20L);

        User user = activeUser(10L);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        // same user, same group → no-op
        assertThatThrownBy(() -> assignmentService.reassign(1L, 10L, 20L, 5L))
                .isInstanceOf(ReassignTargetSameAsCurrentException.class);

        verify(assignmentRepository, never()).flush();
        verify(ticketHistoryService, never()).recordReassigned(any(), any(), any(), any());
    }

    @Test
    void reassign_effectiveGroupFallsBackToTicketGroup_whenBothActiveAndRequestGroupAreNull() {
        ticket.setAssignedGroupId(77L);

        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);
        // active assignment has no group stored
        existing.setAssignedGroupId(null);

        User newUser = activeUser(20L);
        when(userRepository.findById(20L)).thenReturn(Optional.of(newUser));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        Assignment result = assignmentService.reassign(1L, 20L, null, 5L);

        // effective group must fall back to ticket's group
        assertThat(result.getAssignedGroupId()).isEqualTo(77L);
        verify(ticketHistoryService).recordReassigned(eq(1L), eq(5L), eq(20L), eq(77L));
    }

    @Test
    void reassign_secondActiveRowIsNeverInserted() {
        // Verifies old row is closed (unassignedAt set) and only one new active row is created
        Assignment existing = new Assignment();
        existing.setTicketId(1L);
        existing.setAssignedUserId(10L);
        existing.setAssignedGroupId(20L);

        User newUser = activeUser(30L);
        when(userRepository.findById(30L)).thenReturn(Optional.of(newUser));
        when(assignmentRepository.findByTicketIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(existing));
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.save(any())).thenReturn(ticket);

        assignmentService.reassign(1L, 30L, null, 5L);

        // Old row must have unassignedAt set before new row insert
        assertThat(existing.getUnassignedAt()).isNotNull();
        // save called twice on assignmentRepository: once to close old, once to insert new
        var order = inOrder(assignmentRepository);
        order.verify(assignmentRepository).save(existing);
        order.verify(assignmentRepository).flush();
        order.verify(assignmentRepository).save(any(Assignment.class));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User activeUser(Long id) {
        User u = new User();
        u.setIsActive(true);
        return u;
    }
}
