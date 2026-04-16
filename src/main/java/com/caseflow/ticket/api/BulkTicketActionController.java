package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.ticket.api.dto.BulkActionResult;
import com.caseflow.ticket.api.dto.BulkAddTagsRequest;
import com.caseflow.ticket.api.dto.BulkAssignRequest;
import com.caseflow.ticket.api.dto.BulkStatusChangeRequest;
import com.caseflow.ticket.service.BulkTicketActionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bulk ticket operations with partial-success semantics.
 *
 * <p>All endpoints return 200 even when some tickets fail — the caller must check
 * {@code failedCount} and {@code failures} in the response. This avoids ambiguous
 * 207 Multi-Status semantics and keeps FE error handling simple.
 *
 * <p>Max batch size is 100 tickets per request (enforced by DTO validation).
 */
@Tag(name = "Bulk Ticket Actions", description = "Bulk assign, tag, and status operations")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/bulk")
public class BulkTicketActionController {

    private static final Logger log = LoggerFactory.getLogger(BulkTicketActionController.class);

    private final BulkTicketActionService bulkService;

    public BulkTicketActionController(BulkTicketActionService bulkService) {
        this.bulkService = bulkService;
    }

    /**
     * Bulk assign tickets to a user and/or group.
     *
     * <p>At least one of {@code assignedUserId} or {@code assignedGroupId} must be present.
     * Tickets that cannot be reassigned (e.g. not found, invalid actor) are reported in
     * {@code failures} without failing the whole batch.
     */
    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('PERM_TICKET_ASSIGN')")
    public ResponseEntity<BulkActionResult> bulkAssign(
            @Valid @RequestBody BulkAssignRequest request,
            @AuthenticationPrincipal CaseFlowUserDetails principal) {

        log.info("POST /tickets/bulk/assign — count: {}, userId: {}, groupId: {}, actor: {}",
                request.ticketIds().size(), request.assignedUserId(), request.assignedGroupId(),
                principal.getUserId());

        BulkActionResult result = bulkService.bulkAssign(
                request.ticketIds(), request.assignedUserId(),
                request.assignedGroupId(), principal.getUserId());
        return ResponseEntity.ok(result);
    }

    /**
     * Bulk add tags to tickets.
     *
     * <p>Tags are added idempotently — already-tagged tickets are counted as succeeded.
     * Tags that do not exist will cause the entire ticket to be reported as failed.
     */
    @PostMapping("/tags")
    @PreAuthorize("hasAuthority('PERM_TICKET_EDIT')")
    public ResponseEntity<BulkActionResult> bulkAddTags(
            @Valid @RequestBody BulkAddTagsRequest request,
            @AuthenticationPrincipal CaseFlowUserDetails principal) {

        log.info("POST /tickets/bulk/tags — tickets: {}, tags: {}, actor: {}",
                request.ticketIds().size(), request.tagIds(), principal.getUserId());

        BulkActionResult result = bulkService.bulkAddTags(
                request.ticketIds(), request.tagIds(), principal.getUserId());
        return ResponseEntity.ok(result);
    }

    /**
     * Bulk status change.
     *
     * <p>Tickets in states that do not permit the target transition are reported as failures.
     * Valid transitions follow the same state-machine rules as single-ticket changes.
     */
    @PostMapping("/status")
    @PreAuthorize("hasAuthority('PERM_TICKET_EDIT')")
    public ResponseEntity<BulkActionResult> bulkStatusChange(
            @Valid @RequestBody BulkStatusChangeRequest request,
            @AuthenticationPrincipal CaseFlowUserDetails principal) {

        log.info("POST /tickets/bulk/status — count: {}, newStatus: {}, actor: {}",
                request.ticketIds().size(), request.newStatus(), principal.getUserId());

        BulkActionResult result = bulkService.bulkStatusChange(
                request.ticketIds(), request.newStatus(), principal.getUserId());
        return ResponseEntity.ok(result);
    }
}
