package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.email.api.dto.ReplyPreviewRequest;
import com.caseflow.email.api.dto.ReplyPreviewResponse;
import com.caseflow.email.api.dto.UnifiedEmailDetailResponse;
import com.caseflow.email.service.ReplyPreviewService;
import com.caseflow.email.service.TicketEmailDetailService;
import com.caseflow.identity.service.UserService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.service.TicketQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ticket email endpoints that use the stable public UUID for ticket identification.
 *
 * <p>These endpoints complement the existing {@link TicketEmailController} (which uses internal
 * numeric ticket ids) and provide new Phase 1 contract surfaces:
 * <ul>
 *   <li>Unified email detail — type-safe, ticket-scoped detail retrieval</li>
 *   <li>Reply preview — rendered preview before the agent commits to send</li>
 * </ul>
 *
 * <p>All endpoints enforce ticket-level ownership via {@code @ticketAuth} before
 * delegating to the service layer.
 */
@Tag(name = "Ticket Email (Public ID)", description = "Ticket-scoped email detail and reply preview")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets")
public class TicketEmailPublicController {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailPublicController.class);

    private final TicketQueryService ticketQueryService;
    private final TicketEmailDetailService detailService;
    private final ReplyPreviewService previewService;
    private final UserService userService;

    public TicketEmailPublicController(TicketQueryService ticketQueryService,
                                       TicketEmailDetailService detailService,
                                       ReplyPreviewService previewService,
                                       UserService userService) {
        this.ticketQueryService = ticketQueryService;
        this.detailService = detailService;
        this.previewService = previewService;
        this.userService = userService;
    }

    /**
     * Unified email detail endpoint.
     *
     * <p>FE reads {@code detailType} and {@code detailId} from the thread item and calls
     * this endpoint to get the full normalized email detail without needing to know
     * whether the detail lives in MongoDB or PostgreSQL.
     *
     * <p>Ownership: validates that the caller can view the ticket AND that the
     * requested detail belongs to that ticket.
     *
     * @param ticketPublicId the stable public UUID of the ticket
     * @param detailType     EMAIL_DOCUMENT or OUTBOUND_DISPATCH
     * @param detailId       the canonical id from the thread item's {@code detailId} field
     */
    @GetMapping("/{ticketPublicId}/email/detail/{detailType}/{detailId}")
    @PreAuthorize("@ticketAuth.canViewTicketEmailByPublicId(authentication, #ticketPublicId)")
    public ResponseEntity<UnifiedEmailDetailResponse> getEmailDetail(
            @PathVariable UUID ticketPublicId,
            @PathVariable String detailType,
            @PathVariable String detailId) {
        log.info("GET /tickets/{}/email/detail/{}/{}", ticketPublicId, detailType, detailId);
        Ticket ticket = ticketQueryService.getByPublicId(ticketPublicId);
        return ResponseEntity.ok(detailService.resolve(ticket, detailType, detailId));
    }

    /**
     * Reply compose preview endpoint.
     *
     * <p>Returns a fully rendered preview of the reply that would be sent,
     * using the same template rendering and recipient derivation as the real send path.
     * The FE shows this to the agent, who may edit it, then calls the send endpoint
     * with {@code contentWasEdited = true} if they modified the content.
     *
     * @param ticketPublicId the stable public UUID of the ticket
     */
    @PostMapping("/{ticketPublicId}/email/reply/preview")
    @PreAuthorize("@ticketAuth.canSendTicketEmailReplyByPublicId(authentication, #ticketPublicId)")
    public ResponseEntity<ReplyPreviewResponse> previewReply(
            @PathVariable UUID ticketPublicId,
            @Valid @RequestBody ReplyPreviewRequest request,
            @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("POST /tickets/{}/email/reply/preview — mailboxId: {}, sourceEventId: {}",
                ticketPublicId, request.mailboxId(), request.sourceEventId());
        Ticket ticket = ticketQueryService.getByPublicId(ticketPublicId);
        String actorName = resolveActorName(principal);
        return ResponseEntity.ok(previewService.preview(ticket, request, actorName));
    }

    private String resolveActorName(CaseFlowUserDetails principal) {
        if (principal == null) return null;
        try {
            return userService.getById(principal.getUserId()).getFullName();
        } catch (Exception e) {
            return null;
        }
    }
}
