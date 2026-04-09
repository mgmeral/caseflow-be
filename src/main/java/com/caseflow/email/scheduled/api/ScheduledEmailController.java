package com.caseflow.email.scheduled.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.scheduled.api.dto.ScheduleEmailRequest;
import com.caseflow.email.scheduled.api.dto.ScheduledEmailResponse;
import com.caseflow.email.scheduled.service.ScheduledEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ticket-scoped scheduled outbound email endpoints.
 *
 * <p>Authorization: requires {@code PERM_SCHEDULED_EMAIL_MANAGE} plus ticket scope access.
 */
@RestController
@RequestMapping("/api/tickets/{ticketPublicId}/scheduled-emails")
public class ScheduledEmailController {

    private final ScheduledEmailService scheduledEmailService;

    public ScheduledEmailController(ScheduledEmailService scheduledEmailService) {
        this.scheduledEmailService = scheduledEmailService;
    }

    /**
     * Returns all scheduled (including sent and canceled) emails for a ticket.
     */
    @GetMapping
    @PreAuthorize("@ticketAuth.canReadTicketByPublicId(authentication, #ticketPublicId) "
            + "and hasAuthority('PERM_SCHEDULED_EMAIL_MANAGE')")
    public ResponseEntity<List<ScheduledEmailResponse>> listScheduled(
            @PathVariable UUID ticketPublicId) {

        List<ScheduledEmailResponse> result = scheduledEmailService
                .listScheduledForTicket(ticketPublicId)
                .stream().map(ScheduledEmailResponse::from).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Creates a new scheduled outbound email for the ticket.
     */
    @PostMapping
    @PreAuthorize("@ticketAuth.canSendTicketEmailReplyByPublicId(authentication, #ticketPublicId) "
            + "and hasAuthority('PERM_SCHEDULED_EMAIL_MANAGE')")
    public ResponseEntity<ScheduledEmailResponse> scheduleEmail(
            @PathVariable UUID ticketPublicId,
            @Valid @RequestBody ScheduleEmailRequest req,
            @AuthenticationPrincipal CaseFlowUserDetails user) {

        OutboundEmailDispatch dispatch = scheduledEmailService.scheduleEmail(
                ticketPublicId, req.mailboxId(),
                req.sourceEventId(), req.toAddress(),
                req.subject(), req.textBody(), req.htmlBody(),
                req.sendNotBefore(), user.getUserId(),
                req.templateId(), req.templateCode(),
                Boolean.TRUE.equals(req.contentWasEdited())
        );
        return ResponseEntity.status(201).body(ScheduledEmailResponse.from(dispatch));
    }

    /**
     * Cancels a pending scheduled email dispatch.
     */
    @DeleteMapping("/{dispatchId}")
    @PreAuthorize("@ticketAuth.canSendTicketEmailReplyByPublicId(authentication, #ticketPublicId) "
            + "and hasAuthority('PERM_SCHEDULED_EMAIL_MANAGE')")
    public ResponseEntity<ScheduledEmailResponse> cancelScheduledEmail(
            @PathVariable UUID ticketPublicId,
            @PathVariable Long dispatchId,
            @AuthenticationPrincipal CaseFlowUserDetails user) {

        OutboundEmailDispatch dispatch = scheduledEmailService.cancelScheduledEmail(
                ticketPublicId, dispatchId, user.getUserId());
        return ResponseEntity.ok(ScheduledEmailResponse.from(dispatch));
    }
}
