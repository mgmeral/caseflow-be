package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.common.api.PagedResponse;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.ticket.api.dto.QueueStatsResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.repository.TicketScopeSpecification;
import com.caseflow.ticket.service.QueueService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Queue endpoint — triage/assignment workspace for unassigned, actionable tickets.
 *
 * <p>Queue membership: assignedUserId IS NULL AND status NOT IN (RESOLVED, CLOSED).
 * This is the authoritative definition. After assignment, subsequent reads reflect the updated state.
 */
@Tag(name = "Queue", description = "Triage and assignment queue for unassigned tickets")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    /**
     * Returns a page of queue-eligible tickets.
     * Optional {@code priority} filter limits to that priority tier.
     * Caller's ticket scope is enforced.
     */
    @GetMapping
    @PreAuthorize("@ticketAuth.canViewAdminPool(authentication)")
    public ResponseEntity<PagedResponse<TicketSummaryResponse>> getQueue(
            @AuthenticationPrincipal CaseFlowUserDetails user,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String safeSort = ALLOWED_SORTS.contains(sort) ? sort : "createdAt";
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(dir, safeSort));

        Specification<Ticket> scopeSpec = buildQueueScopeSpec(user);
        log.info("GET /queue — priority: {}, page: {}, userId: {}", priority, page,
                user != null ? user.getUserId() : null);

        return ResponseEntity.ok(PagedResponse.from(
                queueService.getQueue(priority, scopeSpec, pageRequest)));
    }

    /**
     * Returns queue chip counts derived from the same queue membership predicate as the list.
     */
    @GetMapping("/stats")
    @PreAuthorize("@ticketAuth.canViewAdminPool(authentication)")
    public ResponseEntity<QueueStatsResponse> getStats(
            @AuthenticationPrincipal CaseFlowUserDetails user) {
        log.info("GET /queue/stats — userId: {}", user != null ? user.getUserId() : null);
        Specification<Ticket> scopeSpec = buildQueueScopeSpec(user);
        return ResponseEntity.ok(queueService.getStats(scopeSpec));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final java.util.Set<String> ALLOWED_SORTS = java.util.Set.of(
            "createdAt", "updatedAt", "statusChangedAt", "priority", "status", "ticketNo");

    private Specification<Ticket> buildQueueScopeSpec(CaseFlowUserDetails user) {
        if (user == null || user.getTicketScope() == null) return null;
        TicketScope scope = TicketScope.valueOf(user.getTicketScope());
        return TicketScopeSpecification.visibleTo(user.getUserId(), user.getGroupIds(), scope);
    }
}
