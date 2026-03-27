package com.caseflow.ticket.api;

import com.caseflow.ticket.api.dto.ChangeTicketStatusRequest;
import com.caseflow.ticket.api.dto.CloseTicketRequest;
import com.caseflow.ticket.api.dto.CreateTicketRequest;
import com.caseflow.ticket.api.dto.ReopenTicketRequest;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.api.dto.UpdateTicketRequest;
import com.caseflow.ticket.api.mapper.TicketMapper;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.service.TicketQueryService;
import com.caseflow.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@Tag(name = "Tickets", description = "Ticket lifecycle management")
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketQueryService ticketQueryService;
    private final TicketMapper ticketMapper;

    public TicketController(TicketService ticketService,
                            TicketQueryService ticketQueryService,
                            TicketMapper ticketMapper) {
        this.ticketService = ticketService;
        this.ticketQueryService = ticketQueryService;
        this.ticketMapper = ticketMapper;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Ticket ticket = ticketService.createTicket(
                request.subject(),
                request.description(),
                request.priority(),
                request.customerId(),
                request.createdBy()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketMapper.toResponse(ticket));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketQueryService.getById(id)));
    }

    @GetMapping("/by-ticket-no/{ticketNo}")
    public ResponseEntity<TicketResponse> getByTicketNo(@PathVariable String ticketNo) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketQueryService.getByTicketNo(ticketNo)));
    }

    @GetMapping
    public ResponseEntity<List<TicketSummaryResponse>> listTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long customerId) {

        List<Ticket> tickets;
        if (status != null) {
            tickets = ticketQueryService.listByStatus(status);
        } else if (userId != null) {
            tickets = ticketQueryService.listByAssignedUser(userId);
        } else if (groupId != null) {
            tickets = ticketQueryService.listByAssignedGroup(groupId);
        } else if (customerId != null) {
            tickets = ticketQueryService.listByCustomer(customerId);
        } else {
            tickets = ticketQueryService.findAll();
        }

        return ResponseEntity.ok(
                tickets.stream().map(ticketMapper::toSummaryResponse).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateTicketRequest request) {
        Ticket ticket = ticketService.updateTicket(
                id,
                request.subject(),
                request.description(),
                request.priority()
        );
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody ChangeTicketStatusRequest request) {
        Ticket ticket = ticketService.changeStatus(id, request.status(), request.performedBy());
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<TicketResponse> closeTicket(@PathVariable Long id,
                                                      @Valid @RequestBody CloseTicketRequest request) {
        Ticket ticket = ticketService.closeTicket(id, request.performedBy());
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<TicketResponse> reopenTicket(@PathVariable Long id,
                                                       @Valid @RequestBody ReopenTicketRequest request) {
        Ticket ticket = ticketService.reopenTicket(id, request.performedBy());
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }
}
