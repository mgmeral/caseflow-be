package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.ticket.api.dto.TicketTagResponse;
import com.caseflow.ticket.service.TagService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manages tag assignments for a specific ticket.
 *
 * <p>All endpoints are scoped to the owning ticket. Authorization is enforced
 * via ticket-level permission checks.
 */
@Tag(name = "Ticket Tags", description = "Tag assignment for tickets")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketId}/tags")
public class TicketTagController {

    private static final Logger log = LoggerFactory.getLogger(TicketTagController.class);

    private final TagService tagService;

    public TicketTagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<List<TicketTagResponse>> listTags(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/tags", ticketId);
        return ResponseEntity.ok(tagService.listTicketTags(ticketId));
    }

    @PostMapping("/{tagId}")
    @PreAuthorize("@ticketAuth.canTagTicket(authentication, #ticketId)")
    public ResponseEntity<TicketTagResponse> addTag(@PathVariable Long ticketId,
                                                    @PathVariable Long tagId,
                                                    @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("POST /tickets/{}/tags/{}", ticketId, tagId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tagService.addTagToTicket(ticketId, tagId, principal.getUserId()));
    }

    @DeleteMapping("/{tagId}")
    @PreAuthorize("@ticketAuth.canTagTicket(authentication, #ticketId)")
    public ResponseEntity<Void> removeTag(@PathVariable Long ticketId,
                                          @PathVariable Long tagId,
                                          @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("DELETE /tickets/{}/tags/{}", ticketId, tagId);
        tagService.removeTagFromTicket(ticketId, tagId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
