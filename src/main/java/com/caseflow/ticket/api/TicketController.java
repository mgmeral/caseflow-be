package com.caseflow.ticket.api;

import com.caseflow.common.api.PagedResponse;
import com.caseflow.common.security.SecurityContextHelper;
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
import com.caseflow.ticket.service.TicketReadService;
import com.caseflow.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Tickets", description = "Ticket lifecycle management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);

    private final TicketService ticketService;
    private final TicketReadService ticketReadService;

    public TicketController(TicketService ticketService, TicketReadService ticketReadService) {
        this.ticketService = ticketService;
        this.ticketReadService = ticketReadService;
    }

    @PostMapping
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
    public ResponseEntity<TicketResponse> getById(@PathVariable Long id) {
        log.info("GET /tickets/{}", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<TicketDetailResponse> getDetail(@PathVariable Long id) {
        log.info("GET /tickets/{}/detail", id);
        return ResponseEntity.ok(ticketReadService.getDetail(id));
    }

    @GetMapping("/by-ticket-no/{ticketNo}")
    public ResponseEntity<TicketResponse> getByTicketNo(@PathVariable String ticketNo) {
        log.info("GET /tickets/by-ticket-no/{}", ticketNo);
        return ResponseEntity.ok(ticketReadService.getResponseByTicketNo(ticketNo));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TicketSummaryResponse>> listTickets(
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

        return ResponseEntity.ok(PagedResponse.from(
                ticketReadService.search(status, priority, userId, groupId, customerId,
                        search, fromInstant, toInstant, pageRequest)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateTicketRequest request) {
        log.info("PUT /tickets/{} — priority: {}", id, request.priority());
        ticketService.updateTicket(id, request.subject(), request.description(), request.priority());
        log.info("PUT /tickets/{} succeeded", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody ChangeTicketStatusRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets/{}/status — newStatus: {}, userId: {}", id, request.status(), userId);
        ticketService.changeStatus(id, request.status(), userId);
        log.info("POST /tickets/{}/status succeeded — status: {}", id, request.status());
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<TicketResponse> closeTicket(@PathVariable Long id,
                                                      @Valid @RequestBody CloseTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets/{}/close — userId: {}", id, userId);
        ticketService.closeTicket(id, userId);
        log.info("POST /tickets/{}/close succeeded", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<TicketResponse> reopenTicket(@PathVariable Long id,
                                                       @Valid @RequestBody ReopenTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        log.info("POST /tickets/{}/reopen — userId: {}", id, userId);
        ticketService.reopenTicket(id, userId);
        log.info("POST /tickets/{}/reopen succeeded", id);
        return ResponseEntity.ok(ticketReadService.getResponse(id));
    }
}
