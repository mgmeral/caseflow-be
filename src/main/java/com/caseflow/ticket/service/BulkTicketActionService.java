package com.caseflow.ticket.service;

import com.caseflow.ticket.api.dto.BulkActionResult;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.workflow.assignment.AssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bulk operations on tickets with partial-success semantics.
 *
 * <p>Each ticket is processed independently in its own transaction via the delegate service.
 * Failures for individual tickets are recorded but do not roll back successfully processed ones.
 * The response always includes both succeeded and failed ticket IDs so the caller can act on
 * partial failures.
 *
 * <h2>Max batch size</h2>
 * Requests are capped at 100 tickets per batch (enforced at the request DTO level with
 * {@code @Size(max = 100)}).
 */
@Service
public class BulkTicketActionService {

    private static final Logger log = LoggerFactory.getLogger(BulkTicketActionService.class);

    private final TicketService ticketService;
    private final AssignmentService assignmentService;
    private final TagService tagService;

    public BulkTicketActionService(TicketService ticketService,
                                    AssignmentService assignmentService,
                                    TagService tagService) {
        this.ticketService = ticketService;
        this.assignmentService = assignmentService;
        this.tagService = tagService;
    }

    /**
     * Assigns / reassigns each ticket in the list.
     *
     * <p>For tickets with an active assignment, calls {@code reassign}. For unassigned tickets,
     * calls {@code assign}. A ticket is skipped (with a failure entry) if neither
     * {@code assignedUserId} nor {@code assignedGroupId} is provided.
     *
     * @param ticketIds      tickets to process
     * @param assignedUserId user to assign to (may be null to assign to group only)
     * @param assignedGroupId group to assign to (may be null to assign to user only)
     * @param actorId        the user performing the bulk action
     */
    public BulkActionResult bulkAssign(List<Long> ticketIds, Long assignedUserId,
                                        Long assignedGroupId, Long actorId) {
        if (assignedUserId == null && assignedGroupId == null) {
            // Invalid request — return all as failed
            Map<Long, String> failures = new LinkedHashMap<>();
            ticketIds.forEach(id -> failures.put(id, "assignedUserId and assignedGroupId cannot both be null"));
            return BulkActionResult.of(List.of(), failures);
        }

        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();

        for (Long ticketId : ticketIds) {
            try {
                // reassign handles both "has active assignment" and "unassigned" cases
                assignmentService.reassign(ticketId, assignedUserId, assignedGroupId, actorId);
                succeeded.add(ticketId);
            } catch (Exception e) {
                log.debug("BULK_ASSIGN failed for ticketId: {} — {}", ticketId, e.getMessage());
                failures.put(ticketId, e.getMessage());
            }
        }

        log.info("BULK_ASSIGN — succeeded: {}, failed: {}, actor: {}",
                succeeded.size(), failures.size(), actorId);
        return BulkActionResult.of(succeeded, failures);
    }

    /**
     * Adds each tag to each ticket idempotently (duplicate assignments are silently skipped).
     *
     * @param ticketIds tickets to tag
     * @param tagIds    tags to add
     * @param actorId   the user performing the bulk action
     */
    public BulkActionResult bulkAddTags(List<Long> ticketIds, List<Long> tagIds, Long actorId) {
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();

        for (Long ticketId : ticketIds) {
            try {
                for (Long tagId : tagIds) {
                    tagService.addTagToTicket(ticketId, tagId, actorId);
                }
                succeeded.add(ticketId);
            } catch (Exception e) {
                log.debug("BULK_ADD_TAGS failed for ticketId: {} — {}", ticketId, e.getMessage());
                failures.put(ticketId, e.getMessage());
            }
        }

        log.info("BULK_ADD_TAGS — tagIds: {}, succeeded: {}, failed: {}, actor: {}",
                tagIds, succeeded.size(), failures.size(), actorId);
        return BulkActionResult.of(succeeded, failures);
    }

    /**
     * Changes the status of each ticket.
     *
     * <p>Tickets in a state that does not permit the transition are reported as failures
     * without affecting other tickets in the batch.
     *
     * @param ticketIds tickets to process
     * @param newStatus target status
     * @param actorId   the user performing the bulk action
     */
    public BulkActionResult bulkStatusChange(List<Long> ticketIds, TicketStatus newStatus,
                                              Long actorId) {
        List<Long> succeeded = new ArrayList<>();
        Map<Long, String> failures = new LinkedHashMap<>();

        for (Long ticketId : ticketIds) {
            try {
                ticketService.changeStatus(ticketId, newStatus, actorId);
                succeeded.add(ticketId);
            } catch (Exception e) {
                log.debug("BULK_STATUS_CHANGE failed for ticketId: {} — {}", ticketId, e.getMessage());
                failures.put(ticketId, e.getMessage());
            }
        }

        log.info("BULK_STATUS_CHANGE — newStatus: {}, succeeded: {}, failed: {}, actor: {}",
                newStatus, succeeded.size(), failures.size(), actorId);
        return BulkActionResult.of(succeeded, failures);
    }
}
