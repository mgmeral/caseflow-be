package com.caseflow.workflow.assignment;

import com.caseflow.common.exception.ActiveAssignmentAlreadyExistsException;
import com.caseflow.common.exception.ActiveAssignmentNotFoundException;
import com.caseflow.common.exception.ReassignTargetSameAsCurrentException;
import com.caseflow.common.exception.ReassignTargetUserInactiveException;
import com.caseflow.common.exception.ReassignTargetUserNotFoundException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.notification.domain.NotificationType;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.domain.Assignment;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.repository.AssignmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    // Concurrency note: findByTicketIdAndUnassignedAtIsNull followed by save is not atomic.
    // Two concurrent assign calls for the same ticket can both pass the active-assignment check.
    // Mitigation requires both: @Version on Assignment entity (optimistic locking) and a
    // DB-level partial unique index on (ticket_id) WHERE unassigned_at IS NULL.

    private final AssignmentRepository assignmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketHistoryService ticketHistoryService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             TicketRepository ticketRepository,
                             UserRepository userRepository,
                             TicketHistoryService ticketHistoryService,
                             NotificationService notificationService,
                             ApplicationEventPublisher eventPublisher) {
        this.assignmentRepository = assignmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.ticketHistoryService = ticketHistoryService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Assignment assign(Long ticketId, Long userId, Long groupId, Long assignedBy) {
        if (userId == null && groupId == null) {
            throw new IllegalArgumentException("Assignment must target at least one of: userId, groupId");
        }
        log.info("Assigning ticket {} — userId: {}, groupId: {}, assignedBy: {}", ticketId, userId, groupId, assignedBy);
        if (assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId).isPresent()) {
            log.warn("Assign failed — active assignment already exists for ticket {}", ticketId);
            throw new ActiveAssignmentAlreadyExistsException(ticketId);
        }

        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setAssignedUserId(userId);
        // Only update group when explicitly provided — never null-out an existing group assignment
        if (groupId != null) {
            ticket.setAssignedGroupId(groupId);
        }
        // Auto-transition to ASSIGNED when assigning a user to an unstarted ticket
        if (userId != null) {
            TicketStatus current = ticket.getStatus();
            if (current == TicketStatus.NEW
                    || current == TicketStatus.TRIAGED
                    || current == TicketStatus.REOPENED) {
                ticket.setStatus(TicketStatus.ASSIGNED);
            }
        }
        ticketRepository.save(ticket);

        Long effectiveGroupId = groupId != null ? groupId : ticket.getAssignedGroupId();
        Assignment assignment = buildAssignment(ticketId, userId, effectiveGroupId, assignedBy);
        Assignment saved = assignmentRepository.save(assignment);

        ticketHistoryService.recordAssigned(ticketId, assignedBy, userId, groupId);

        // Notifications: notify group members and/or the assigned user
        if (groupId != null) {
            notificationService.notifyGroupTicketCreated(
                    ticketId, ticket.getPublicId(), ticket.getTicketNo(), groupId, assignedBy);
        }
        if (userId != null) {
            notificationService.notifyUserAssigned(
                    ticketId, ticket.getPublicId(), ticket.getTicketNo(),
                    userId, NotificationType.TICKET_ASSIGNED_TO_USER, assignedBy);
        }

        eventPublisher.publishEvent(new TicketDomainEvent(ticketId, ticket.getPublicId(),
                NotificationEventType.TICKET_ASSIGNED, assignedBy,
                ticket.getCustomerId(), ticket.getAssignedGroupId()));
        log.info("Ticket {} assigned — userId: {}, groupId: {}", ticketId, userId, groupId);
        return saved;
    }

    @Transactional
    public Assignment reassign(Long ticketId, Long newUserId, Long newGroupId, Long reassignedBy) {
        if (newUserId == null && newGroupId == null) {
            throw new IllegalArgumentException("Reassignment must target at least one of: newUserId, newGroupId");
        }
        log.info("Reassigning ticket {} — newUserId: {}, requestedNewGroupId: {}, reassignedBy: {}",
                ticketId, newUserId, newGroupId, reassignedBy);

        // Guard: ticket must exist
        Ticket ticket = findTicketOrThrow(ticketId);

        // Guard: active assignment must exist — reassign without a current assignment is a logic error
        Assignment active = assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId)
                .orElseThrow(() -> {
                    log.warn("Reassign failed — no active assignment for ticket {}", ticketId);
                    return new ActiveAssignmentNotFoundException(ticketId);
                });

        // Guard: validate new target user when provided
        if (newUserId != null) {
            User newUser = userRepository.findById(newUserId)
                    .orElseThrow(() -> new ReassignTargetUserNotFoundException(newUserId));
            if (!Boolean.TRUE.equals(newUser.getIsActive())) {
                throw new ReassignTargetUserInactiveException(newUserId);
            }
        }

        // Compute effective group: explicit request wins; fall back to active assignment's group,
        // then ticket's group — never null-out group context.
        Long effectiveGroupId = newGroupId != null ? newGroupId
                : active.getAssignedGroupId() != null ? active.getAssignedGroupId()
                : ticket.getAssignedGroupId();

        // Guard: same-target reassign is a no-op — reject before touching DB
        if (Objects.equals(active.getAssignedUserId(), newUserId)
                && Objects.equals(active.getAssignedGroupId(), effectiveGroupId)) {
            log.warn("Reassign rejected — ticket {} already assigned to userId={}, groupId={}",
                    ticketId, newUserId, effectiveGroupId);
            throw new ReassignTargetSameAsCurrentException(ticketId);
        }

        log.debug("Reassign ticket {} — currentAssignmentId: {}, currentUserId: {}, newUserId: {}, requestedGroupId: {}, effectiveGroupId: {}",
                ticketId, active.getId(), active.getAssignedUserId(), newUserId, newGroupId, effectiveGroupId);

        Long previousUserId = active.getAssignedUserId();

        // Close current active row and flush to DB BEFORE inserting the new active row.
        // Without flush(), Hibernate defers the UPDATE until transaction commit; the subsequent
        // INSERT would find two active rows for the same ticket, violating the partial unique index.
        active.setUnassignedAt(Instant.now());
        assignmentRepository.save(active);
        assignmentRepository.flush();

        // Update ticket fields
        ticket.setAssignedUserId(newUserId);
        // Only update group when explicitly provided — never null-out an existing group assignment
        if (newGroupId != null) {
            ticket.setAssignedGroupId(newGroupId);
        }
        // Auto-transition to ASSIGNED when assigning a user to an unstarted ticket
        if (newUserId != null) {
            TicketStatus current = ticket.getStatus();
            if (current == TicketStatus.NEW
                    || current == TicketStatus.TRIAGED
                    || current == TicketStatus.REOPENED) {
                ticket.setStatus(TicketStatus.ASSIGNED);
            }
        }
        ticketRepository.save(ticket);

        // Insert new active assignment row using effective group
        Assignment assignment = buildAssignment(ticketId, newUserId, effectiveGroupId, reassignedBy);
        Assignment saved = assignmentRepository.save(assignment);

        // History and notification use the same effective group
        ticketHistoryService.recordReassigned(ticketId, reassignedBy, newUserId, effectiveGroupId);

        // Notify only when the assigned user actually changed
        if (newUserId != null && !Objects.equals(previousUserId, newUserId)) {
            notificationService.notifyUserAssigned(
                    ticketId, ticket.getPublicId(), ticket.getTicketNo(),
                    newUserId, NotificationType.TICKET_REASSIGNED_TO_USER, reassignedBy);
        }

        log.info("Ticket {} reassigned — newUserId: {}, effectiveGroupId: {}", ticketId, newUserId, effectiveGroupId);
        return saved;
    }

    @Transactional
    public void unassign(Long ticketId, Long performedBy) {
        log.info("Unassigning ticket {} — performedBy: {}", ticketId, performedBy);
        Assignment active = assignmentRepository.findByTicketIdAndUnassignedAtIsNull(ticketId)
                .orElseThrow(() -> {
                    log.warn("Unassign failed — no active assignment for ticket {}", ticketId);
                    return new IllegalStateException("No active assignment found for ticket: " + ticketId);
                });
        active.setUnassignedAt(Instant.now());
        assignmentRepository.save(active);

        Ticket ticket = findTicketOrThrow(ticketId);
        ticket.setAssignedUserId(null);
        // Group assignment is deliberately preserved on unassign — only the user link is removed.
        // The group retains ownership so the ticket stays visible in the group queue.
        ticketRepository.save(ticket);

        ticketHistoryService.recordUnassigned(ticketId, performedBy);
        log.info("Ticket {} unassigned", ticketId);
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
