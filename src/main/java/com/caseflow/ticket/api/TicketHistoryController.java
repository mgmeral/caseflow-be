package com.caseflow.ticket.api;

import com.caseflow.ticket.api.dto.HistoryResponse;
import com.caseflow.ticket.api.mapper.HistoryMapper;
import com.caseflow.ticket.domain.History;
import com.caseflow.workflow.history.TicketHistoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes the ticket event / operation log.
 *
 * <p>Uses the stable {@code ticketPublicId} (UUID) in the URL so callers are
 * not coupled to the internal numeric ticket ID.
 */
@Tag(name = "Ticket History", description = "Ticket operation log / event stream")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketPublicId}/history")
public class TicketHistoryController {

    private static final Logger log = LoggerFactory.getLogger(TicketHistoryController.class);

    private final TicketHistoryService historyService;
    private final HistoryMapper historyMapper;

    public TicketHistoryController(TicketHistoryService historyService,
                                   HistoryMapper historyMapper) {
        this.historyService = historyService;
        this.historyMapper = historyMapper;
    }

    /**
     * Returns the full chronological event log for a ticket.
     * Events are ordered oldest-first so UI can render a timeline naturally.
     */
    @GetMapping
    @PreAuthorize("@ticketAuth.canReadTicketByPublicId(authentication, #ticketPublicId)")
    public ResponseEntity<List<HistoryResponse>> getHistory(
            @PathVariable UUID ticketPublicId) {
        log.info("GET /tickets/{}/history", ticketPublicId);
        List<History> events = historyService.getByTicketPublicId(ticketPublicId);
        List<HistoryResponse> responses = events.stream()
                .map(historyMapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
