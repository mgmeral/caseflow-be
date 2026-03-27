package com.caseflow.ticket.api;

import com.caseflow.common.api.PagedResponse;
import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.ChangeTicketStatusRequest;
import com.caseflow.ticket.api.dto.CloseTicketRequest;
import com.caseflow.ticket.api.dto.CreateTicketRequest;
import com.caseflow.ticket.api.dto.ReopenTicketRequest;
import com.caseflow.ticket.api.dto.TicketDetailResponse;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.api.dto.UpdateTicketRequest;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.api.mapper.HistoryMapper;
import com.caseflow.ticket.api.mapper.TicketMapper;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.HistoryRepository;
import com.caseflow.ticket.service.TicketQueryService;
import com.caseflow.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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

    private final TicketService ticketService;
    private final TicketQueryService ticketQueryService;
    private final TicketMapper ticketMapper;
    private final AttachmentMetadataMapper attachmentMetadataMapper;
    private final AttachmentService attachmentService;
    private final HistoryMapper historyMapper;
    private final HistoryRepository historyRepository;

    public TicketController(TicketService ticketService,
                            TicketQueryService ticketQueryService,
                            TicketMapper ticketMapper,
                            AttachmentMetadataMapper attachmentMetadataMapper,
                            AttachmentService attachmentService,
                            HistoryMapper historyMapper,
                            HistoryRepository historyRepository) {
        this.ticketService = ticketService;
        this.ticketQueryService = ticketQueryService;
        this.ticketMapper = ticketMapper;
        this.attachmentMetadataMapper = attachmentMetadataMapper;
        this.attachmentService = attachmentService;
        this.historyMapper = historyMapper;
        this.historyRepository = historyRepository;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        Ticket ticket = ticketService.createTicket(
                request.subject(), request.description(), request.priority(), request.customerId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketMapper.toResponse(ticket));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketQueryService.getById(id)));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<TicketDetailResponse> getDetail(@PathVariable Long id) {
        Ticket ticket = ticketQueryService.getById(id);
        TicketDetailResponse detail = new TicketDetailResponse(
                ticket.getId(), ticket.getTicketNo(), ticket.getSubject(), ticket.getDescription(),
                ticket.getStatus(), ticket.getPriority(), ticket.getCustomerId(),
                ticket.getAssignedUserId(), ticket.getAssignedGroupId(),
                ticket.getCreatedAt(), ticket.getUpdatedAt(), ticket.getClosedAt(),
                attachmentMetadataMapper.toResponseList(attachmentService.findByTicketId(id)),
                historyMapper.toSummaryResponseList(historyRepository.findByTicketIdOrderByPerformedAtAsc(id))
        );
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/by-ticket-no/{ticketNo}")
    public ResponseEntity<TicketResponse> getByTicketNo(@PathVariable String ticketNo) {
        return ResponseEntity.ok(ticketMapper.toResponse(ticketQueryService.getByTicketNo(ticketNo)));
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

        Page<Ticket> ticketPage = ticketQueryService.search(
                status, priority, userId, groupId, customerId, search, fromInstant, toInstant, pageRequest);

        return ResponseEntity.ok(PagedResponse.from(ticketPage.map(ticketMapper::toSummaryResponse)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateTicketRequest request) {
        Ticket ticket = ticketService.updateTicket(id, request.subject(), request.description(), request.priority());
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody ChangeTicketStatusRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        Ticket ticket = ticketService.changeStatus(id, request.status(), userId);
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<TicketResponse> closeTicket(@PathVariable Long id,
                                                      @Valid @RequestBody CloseTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        Ticket ticket = ticketService.closeTicket(id, userId);
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }

    @PostMapping("/{id}/reopen")
    public ResponseEntity<TicketResponse> reopenTicket(@PathVariable Long id,
                                                       @Valid @RequestBody ReopenTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        Ticket ticket = ticketService.reopenTicket(id, userId);
        return ResponseEntity.ok(ticketMapper.toResponse(ticket));
    }
}
