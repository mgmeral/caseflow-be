package com.caseflow.ticket.api;

import com.caseflow.ticket.api.dto.TicketAssistResponse;
import com.caseflow.ticket.service.TicketAssistService;
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

/**
 * Rule-based reply assist for agent compose flow.
 *
 * <p>Returns recommended templates, suggested next statuses, and allowed state-machine
 * transitions for the given ticket. Purely read-only — no side effects.
 */
@Tag(name = "Ticket Assist", description = "Rule-based reply and status suggestions for agents")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketId}/assist")
public class TicketAssistController {

    private static final Logger log = LoggerFactory.getLogger(TicketAssistController.class);

    private final TicketAssistService assistService;

    public TicketAssistController(TicketAssistService assistService) {
        this.assistService = assistService;
    }

    /**
     * Returns context-aware assist hints for the given ticket.
     *
     * <p>Response includes:
     * <ul>
     *   <li>{@code recommendedTemplates} — up to 3 relevant mail templates</li>
     *   <li>{@code suggestedNextStatuses} — primary next statuses for the current state</li>
     *   <li>{@code allowedTransitions} — full valid transition set from the state machine</li>
     * </ul>
     */
    @GetMapping
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<TicketAssistResponse> assist(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/assist", ticketId);
        return ResponseEntity.ok(assistService.assist(ticketId));
    }
}
