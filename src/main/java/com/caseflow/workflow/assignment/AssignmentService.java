package com.caseflow.workflow.assignment;

import com.caseflow.common.exception.ActiveAssignmentAlreadyExistsException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.domain.Assignment;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AssignmentService {

    // Concurrency note: findByTicketIdAndUnassignedAtIsNull followed by save is not atomic.
    // Two concurrent assign calls for the same ticket can both pass the active-assignment check.
    // Mitigation requires both: @Version on Assignment entity (optimistic locking) and a
    // DB-level partial unique index on (ticket_id) WHERE unassigned_at IS NULL.

    private final AssignmentRepository assignmentRepository;
    private final TicketRepository ticketRepository;
    private final TicketHistoryService ticketHistoryService;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             TicketRepository ticketRepository,
                             TicketHistoryService ticketHistoryService) {
        this.assignmentRepository = assignmentRepository;
        this.ticketRepository = ticketRepository;
        this.ticketHistoryService = ticketHistoryService;
    }

    @Transactional
    public Assignment assign(Long ticketId, Long userId, Long groupId, Long assignedBy) {
        if (assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId).isPresent()) {
            throw new ActiveAssignmentAlreadyExistsException(ticketId);
        }

        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setAssignedUserId(userId);
        ticket.setAssignedGroupId(groupId);
        ticketRepository.save(ticket);

        Assignment assignment = buildAssignment(ticketId, userId, groupId, assignedBy);
        Assignment saved = assignmentRepository.save(assignment);

        ticketHistoryService.recordAssigned(ticketId, assignedBy, userId, groupId);
        return saved;
    }

    @Transactional
    public Assignment reassign(Long ticketId, Long newUserId, Long newGroupId, Long reassignedBy) {
        assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId).ifPresent(active -> {
            active.setUnassignedAt(Instant.now());
            assignmentRepository.save(active);
        });

        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setAssignedUserId(newUserId);
        ticket.setAssignedGroupId(newGroupId);
        ticketRepository.save(ticket);

        Assignment assignment = buildAssignment(ticketId, newUserId, newGroupId, reassignedBy);
        Assignment saved = assignmentRepository.save(assignment);

        ticketHistoryService.recordReassigned(ticketId, reassignedBy, newUserId, newGroupId);
        return saved;
    }

    @Transactional
    public void unassign(Long ticketId, Long performedBy) {
        Assignment active = assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId)
                .orElseThrow(() -> new IllegalStateException(
                        "No active assignment found for ticket: " + ticketId));
        active.setUnassignedAt(Instant.now());
        assignmentRepository.save(active);

        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setAssignedUserId(null);
        ticket.setAssignedGroupId(null);
        ticketRepository.save(ticket);

        ticketHistoryService.recordUnassigned(ticketId, performedBy);
    }

    @Transactional(readOnly = true)
    public Optional<Assignment> getActiveAssignment(Long ticketId) {
        return assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId);
    }

    private Assignment buildAssignment(Long ticketId, Long userId, Long groupId, Long assignedBy) {
        Assignment assignment = new Assignment();
        assignment.setTicketId(ticketId);
        assignment.setAssignedUserId(userId);
        assignment.setAssignedGroupId(groupId);
        assignment.setAssignedBy(assignedBy);
        return assignment;
    }

    private Ticket findTicketOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }
}
