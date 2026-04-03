package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.email.api.dto.DispatchResponse;
import com.caseflow.email.api.dto.EmailThreadItem;
import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.dto.ReplyEnqueuedResponse;
import com.caseflow.email.api.dto.SendReplyRequest;
import com.caseflow.email.api.mapper.DispatchMapper;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.OutboundEmailDispatch;
import java.time.Instant;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailDocumentQueryService;
import com.caseflow.email.service.EmailIngressEventQueryService;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.EmailReplyService;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.repository.AttachmentMetadataRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Tag(name = "Ticket Email", description = "Email thread and outbound replies for tickets")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketId}/email")
public class TicketEmailController {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailController.class);

    private static final int BODY_PREVIEW_MAX = 500;

    private final EmailIngressEventQueryService ingressQueryService;
    private final EmailDispatchService dispatchService;
    private final EmailDocumentQueryService docQueryService;
    private final EmailReplyService replyService;
    private final EmailMailboxService mailboxService;
    private final AttachmentMetadataRepository attachmentMetadataRepository;
    private final IngressEventMapper ingressMapper;
    private final DispatchMapper dispatchMapper;

    public TicketEmailController(EmailIngressEventQueryService ingressQueryService,
                                  EmailDispatchService dispatchService,
                                  EmailDocumentQueryService docQueryService,
                                  EmailReplyService replyService,
                                  EmailMailboxService mailboxService,
                                  AttachmentMetadataRepository attachmentMetadataRepository,
                                  IngressEventMapper ingressMapper,
                                  DispatchMapper dispatchMapper) {
        this.ingressQueryService = ingressQueryService;
        this.dispatchService = dispatchService;
        this.docQueryService = docQueryService;
        this.replyService = replyService;
        this.mailboxService = mailboxService;
        this.attachmentMetadataRepository = attachmentMetadataRepository;
        this.ingressMapper = ingressMapper;
        this.dispatchMapper = dispatchMapper;
    }

    /**
     * Primary FE contract — unified chronological thread of all inbound events
     * and outbound dispatches associated with this ticket.
     */
    @GetMapping("/thread")
    @PreAuthorize("@ticketAuth.canViewTicketEmail(authentication, #ticketId)")
    public ResponseEntity<List<EmailThreadItem>> getThread(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/email/thread", ticketId);
        List<EmailThreadItem> items = new ArrayList<>();

        ingressQueryService.findByTicketId(ticketId).forEach(event -> {
            String preview = event.getDocumentId() != null
                    ? docQueryService.findById(event.getDocumentId())
                            .map(doc -> doc.getBodyPreview()).orElse(null)
                    : null;
            int attachmentCount = event.getDocumentId() != null
                    ? docQueryService.findById(event.getDocumentId())
                            .map(doc -> doc.getAttachments() != null ? doc.getAttachments().size() : 0)
                            .orElse(0)
                    : 0;
            items.add(new EmailThreadItem(
                    "INBOUND",
                    event.getId(),
                    event.getMessageId(),
                    event.getRawFrom(),
                    null,
                    event.getRawSubject(),
                    event.getStatus().name(),
                    event.getReceivedAt(),
                    preview,
                    attachmentCount
            ));
        });

        dispatchService.findByTicketId(ticketId).forEach(dispatch -> {
            String rawBody = dispatch.getTextBody();
            String preview = rawBody != null
                    ? rawBody.substring(0, Math.min(BODY_PREVIEW_MAX, rawBody.length()))
                    : null;
            items.add(new EmailThreadItem(
                    "OUTBOUND",
                    dispatch.getId(),
                    dispatch.getMessageId(),
                    dispatch.getFromAddress(),
                    dispatch.getToAddress(),
                    dispatch.getSubject(),
                    dispatch.getStatus().name(),
                    dispatch.getSentAt() != null ? dispatch.getSentAt() : dispatch.getCreatedAt(),
                    preview,
                    0
            ));
        });

        items.sort(Comparator.comparing(EmailThreadItem::timestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ResponseEntity.ok(items);
    }

    /**
     * Detail view for a specific inbound event.
     * Verifies the event belongs to the path ticketId.
     */
    @GetMapping("/inbound/{eventId}")
    @PreAuthorize("@ticketAuth.canViewTicketEmail(authentication, #ticketId)")
    public ResponseEntity<IngressEventResponse> getInboundEvent(@PathVariable Long ticketId,
                                                                 @PathVariable Long eventId) {
        log.info("GET /tickets/{}/email/inbound/{}", ticketId, eventId);
        EmailIngressEvent event = ingressQueryService.getById(eventId);
        if (!ticketId.equals(event.getTicketId())) {
            log.warn("Ownership mismatch — eventId: {} belongs to ticketId: {}, requested under ticketId: {}",
                    eventId, event.getTicketId(), ticketId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Inbound event " + eventId + " not found under ticket " + ticketId);
        }
        return ResponseEntity.ok(ingressMapper.toResponse(event));
    }

    /**
     * Detail view for a specific outbound dispatch.
     * Verifies the dispatch belongs to the path ticketId.
     */
    @GetMapping("/outbound/{dispatchId}")
    @PreAuthorize("@ticketAuth.canViewTicketEmail(authentication, #ticketId)")
    public ResponseEntity<DispatchResponse> getOutboundDispatch(@PathVariable Long ticketId,
                                                                 @PathVariable Long dispatchId) {
        log.info("GET /tickets/{}/email/outbound/{}", ticketId, dispatchId);
        OutboundEmailDispatch dispatch = dispatchService.getById(dispatchId);
        if (!ticketId.equals(dispatch.getTicketId())) {
            log.warn("Ownership mismatch — dispatchId: {} belongs to ticketId: {}, requested under ticketId: {}",
                    dispatchId, dispatch.getTicketId(), ticketId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Outbound dispatch " + dispatchId + " not found under ticket " + ticketId);
        }
        return ResponseEntity.ok(dispatchMapper.toResponse(dispatch));
    }

    // ── Backward-compatible list endpoints ───────────────────────────────────

    /** @deprecated Use GET /thread for the unified timeline. Kept for backward compatibility. */
    @GetMapping("/inbound")
    @PreAuthorize("@ticketAuth.canViewTicketEmail(authentication, #ticketId)")
    public ResponseEntity<List<IngressEventResponse>> getInboundEvents(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/email/inbound (list)", ticketId);
        return ResponseEntity.ok(
                ingressMapper.toResponseList(ingressQueryService.findByTicketId(ticketId)));
    }

    /** @deprecated Use GET /thread for the unified timeline. Kept for backward compatibility. */
    @GetMapping("/dispatches")
    @PreAuthorize("@ticketAuth.canViewTicketEmail(authentication, #ticketId)")
    public ResponseEntity<List<DispatchResponse>> getDispatches(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/email/dispatches (list)", ticketId);
        return ResponseEntity.ok(
                dispatchMapper.toResponseList(dispatchService.findByTicketId(ticketId)));
    }

    @PostMapping("/reply")
    @PreAuthorize("@ticketAuth.canSendTicketEmailReply(authentication, #ticketId)")
    public ResponseEntity<ReplyEnqueuedResponse> sendReply(
            @PathVariable Long ticketId,
            @Valid @RequestBody SendReplyRequest request,
            @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("POST /tickets/{}/email/reply — mailboxId: {}, sourceEventId: {}, toAddress: '{}'",
                ticketId, request.mailboxId(), request.sourceEventId(), request.toAddress());

        if (request.sourceEventId() == null && (request.toAddress() == null || request.toAddress().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide either sourceEventId (preferred) or toAddress");
        }

        String fromAddress = mailboxService.getById(request.mailboxId()).getAddress();

        OutboundEmailDispatch dispatch = replyService.sendReply(
                ticketId,
                request.mailboxId(),
                request.sourceEventId(),
                request.toAddress(),
                fromAddress,
                request.subject(),
                request.textBody(),
                request.htmlBody(),
                request.inReplyToMessageId(),
                principal.getUserId()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ReplyEnqueuedResponse(
                dispatch.getId(),
                dispatch.getResolvedToAddress(),
                dispatch.getMailboxId(),
                Instant.now()
        ));
    }
}
