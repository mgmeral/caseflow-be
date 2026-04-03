package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.common.api.PagedResponse;
import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.ticket.api.dto.AllowedTransitionsResponse;
import com.caseflow.ticket.api.dto.ChangeTicketStatusRequest;
import com.caseflow.ticket.api.dto.CloseTicketRequest;
import com.caseflow.ticket.api.dto.CreateTicketRequest;
import com.caseflow.ticket.api.dto.ReopenTicketRequest;
import com.caseflow.ticket.api.dto.TicketDetailResponse;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.api.dto.UpdateTicketRequest;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketScopeSpecification;
import com.caseflow.ticket.service.TicketQueryService;
import com.caseflow.ticket.service.TicketReadService;
import com.caseflow.ticket.service.TicketService;
import com.caseflow.workflow.state.TicketStateMachineService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Tickets", description = "Ticket lifecycle management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    private final TicketService ticketService;
    private final TicketReadService ticketReadService;
    private final TicketQueryService ticketQueryService;
    private final TicketStateMachineService stateMachine;
    private final NotificationService notificationService;

    public TicketController(TicketService ticketService, TicketReadService ticketReadService,
                            TicketQueryService ticketQueryService, TicketStateMachineService stateMachine,
                            NotificationService notificationService) {
        this.ticketService = ticketService;
        this.ticketReadService = ticketReadService;
        this.ticketQueryService = ticketQueryService;
        this.stateMachine = stateMachine;
        this.notificationService = notificationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_TICKET_STATUS_CHANGE')")
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets — subject: '{}', priority: {}, customerId: {}, userId: {}",
                request.subject(), request.priority(), request.customerId(), userId);
        Ticket ticket = ticketService.createTicket(
                request.subject(), request.description(), request.priority(), request.customerId(), userId);
        log.info("POST /tickets succeeded — ticketId: {}, ticketNo: {}", ticket.getId(), ticket.getTicketNo());
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketReadService.getResponse(ticket.getId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #id)")
    public ResponseEntity<TicketResponse> getById(@PathVariable Long id) {
        log.info("GET /tickets/{}", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #id)")
    public ResponseEntity<TicketDetailResponse> getDetail(@PathVariable Long id,
                                                           @AuthenticationPrincipal CaseFlowUserDetails user) {
        log.info("GET /tickets/{}/detail — userId: {}", id, user != null ? user.getUserId() : null);
        if (user != null) {
            notificationService.markReadByTicket(user.getUserId(), id);
        }
        return ResponseEntity.ok(ticketReadService.getDetail(id));
    }

    /**
     * Explicitly marks all unread notifications for this ticket as read for the current user.
     * Callers who prefer explicit read-marking over the auto-clear on GET /detail can use this.
     */
    @PostMapping("/{id}/notifications/read")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #id)")
    public ResponseEntity<Void> markNotificationsRead(@PathVariable Long id,
                                                       @AuthenticationPrincipal CaseFlowUserDetails user) {
        log.info("POST /tickets/{}/notifications/read — userId: {}", id, user.getUserId());
        notificationService.markReadByTicket(user.getUserId(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/by-ticket-no/{ticketNo}")
    @PreAuthorize("@ticketAuth.canReadTicketByNo(authentication, #ticketNo)")
    public ResponseEntity<TicketResponse> getByTicketNo(@PathVariable String ticketNo) {
        log.info("GET /tickets/by-ticket-no/{}", ticketNo);
        return ResponseEntity.ok(ticketReadService.getResponseByTicketNo(ticketNo));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_TICKET_READ')")
    public ResponseEntity<PagedResponse<TicketSummaryResponse>> listTickets(
            @AuthenticationPrincipal CaseFlowUserDetails user,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(dir, sort));

        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant   = to   != null ? Instant.parse(to)   : null;

        Specification<Ticket> scopeSpec = buildScopeSpec(user);

        return ResponseEntity.ok(PagedResponse.from(
                ticketReadService.search(status, priority, userId, groupId, customerId,
                        search, fromInstant, toInstant, scopeSpec, pageRequest)));
    }

    @GetMapping("/admin-pool")
    @PreAuthorize("@ticketAuth.canViewAdminPool(authentication)")
    public ResponseEntity<PagedResponse<TicketSummaryResponse>> listAdminPool(
            @AuthenticationPrincipal CaseFlowUserDetails user,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(dir, sort));

        Specification<Ticket> spec = buildAdminPoolSpec(user);

        return ResponseEntity.ok(PagedResponse.from(ticketReadService.searchScoped(spec, pageRequest)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ticketAuth.canChangePriority(authentication, #id)")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateTicketRequest request) {
        log.info("PUT /tickets/{} — priority: {}", id, request.priority());
        ticketService.updateTicket(id, request.subject(), request.description(), request.priority());
        log.info("PUT /tickets/{} succeeded", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("@ticketAuth.canChangeTicketStatus(authentication, #id)")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody ChangeTicketStatusRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets/{}/status — newStatus: {}, userId: {}", id, request.status(), userId);
        ticketService.changeStatus(id, request.status(), userId);
        log.info("POST /tickets/{}/status succeeded — status: {}", id, request.status());
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@ticketAuth.canCloseTicket(authentication, #id)")
    public ResponseEntity<TicketResponse> closeTicket(@PathVariable Long id,
                                                      @Valid @RequestBody CloseTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets/{}/close — userId: {}", id, userId);
        ticketService.closeTicket(id, userId);
        log.info("POST /tickets/{}/close succeeded", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("@ticketAuth.canChangeTicketStatus(authentication, #id)")
    public ResponseEntity<TicketResponse> reopenTicket(@PathVariable Long id,
                                                       @Valid @RequestBody ReopenTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets/{}/reopen — userId: {}", id, userId);
        ticketService.reopenTicket(id, userId);
        log.info("POST /tickets/{}/reopen succeeded", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    /**
     * Returns the allowed manual status transitions for the given ticket.
     * FE uses this to render only valid status change options.
     */
    @GetMapping("/{id}/transitions")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #id)")
    public ResponseEntity<AllowedTransitionsResponse> getAllowedTransitions(@PathVariable Long id) {
        log.info("GET /tickets/{}/transitions", id);
        Ticket ticket = ticketQueryService.getById(id);
        return ResponseEntity.ok(new AllowedTransitionsResponse(
                ticket.getId(),
                ticket.getStatus(),
                stateMachine.allowedTransitions(ticket.getStatus())
        ));
    }

    /**
     * Customer reply outbound — not yet implemented.
     * Authorization is enforced (permission + scope); authorized callers get 501.
     * Unauthorized callers get 403. Both responses are honest.
     */
    @PostMapping("/{id}/reply")
    @PreAuthorize("@ticketAuth.canSendCustomerReply(authentication, #id)")
    public ResponseEntity<Void> replyToCustomer(@PathVariable Long id) {
        log.info("POST /tickets/{}/reply — not implemented", id);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ── Scope helpers ─────────────────────────────────────────────────────────

    private Specification<Ticket> buildScopeSpec(CaseFlowUserDetails user) {
        if (user == null || user.getTicketScope() == null) return null;
        TicketScope scope = TicketScope.valueOf(user.getTicketScope());
        return TicketScopeSpecification.visibleTo(user.getUserId(), user.getGroupIds(), scope);
    }

    private Specification<Ticket> buildAdminPoolSpec(CaseFlowUserDetails user) {
        if (user == null || user.getTicketScope() == null) {
            return TicketScopeSpecification.unassignedUser();
        }
        TicketScope scope = TicketScope.valueOf(user.getTicketScope());
        return TicketScopeSpecification.adminPoolVisibleTo(user.getUserId(), user.getGroupIds(), scope);
    }
}
