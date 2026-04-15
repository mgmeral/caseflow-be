package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.email.api.dto.DispatchResponse;
import com.caseflow.email.api.dto.EmailThreadItem;
import com.caseflow.email.api.dto.ReplyEnqueuedResponse;
import com.caseflow.email.api.dto.SendReplyRequest;
import com.caseflow.email.api.mapper.DispatchMapper;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.OutboundEmailDispatch;
import java.time.Instant;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailDocumentQueryService;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.EmailReplyService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Ticket Email", description = "Email thread and outbound replies for tickets")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketId}/email")
public class TicketEmailController {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailController.class);

    private static final int BODY_PREVIEW_MAX = 500;

    private final EmailIngressEventRepository ingressEventRepository;
    private final EmailDispatchService dispatchService;
    private final EmailDocumentQueryService docQueryService;
    private final EmailReplyService replyService;
    private final EmailMailboxService mailboxService;
    private final DispatchMapper dispatchMapper;

    public TicketEmailController(EmailIngressEventRepository ingressEventRepository,
                                  EmailDispatchService dispatchService,
                                  EmailDocumentQueryService docQueryService,
                                  EmailReplyService replyService,
                                  EmailMailboxService mailboxService,
                                  DispatchMapper dispatchMapper) {
        this.ingressEventRepository = ingressEventRepository;
        this.dispatchService = dispatchService;
        this.docQueryService = docQueryService;
        this.replyService = replyService;
        this.mailboxService = mailboxService;
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

        // Prefetch mailbox names once to avoid N+1 lookups
        java.util.Map<Long, String> mailboxNameCache = new java.util.HashMap<>();

        ingressEventRepository.findByTicketId(ticketId).forEach(event -> {
            String docId = event.getDocumentId();
            String preview = null;
            int attachmentCount = 0;
            if (docId != null) {
                java.util.Optional<com.caseflow.email.document.EmailDocument> docOpt =
                        docQueryService.findById(docId);
                preview = docOpt.map(com.caseflow.email.document.EmailDocument::getBodyPreview).orElse(null);
                attachmentCount = docOpt.map(doc -> doc.getAttachments() != null
                        ? doc.getAttachments().size() : 0).orElse(0);
            }
            String mailboxName = resolveMailboxName(event.getMailboxId(), mailboxNameCache);
            items.add(new EmailThreadItem(
                    "INBOUND",
                    event.getId(),
                    event.getMessageId(),
                    event.getRawFrom(),
                    event.getRawTo(),
                    event.getRawSubject(),
                    event.getStatus().name(),
                    event.getReceivedAt(),
                    preview,
                    attachmentCount,
                    // Phase 1 fields
                    docId,
                    event.getId(),
                    event.getMailboxId(),
                    mailboxName,
                    event.getFailureReason(),
                    null,
                    "EMAIL_DOCUMENT",
                    docId != null ? docId : String.valueOf(event.getId()),
                    attachmentCount > 0,
                    preview != null && !preview.isBlank()
            ));
        });

        dispatchService.findByTicketId(ticketId).forEach(dispatch -> {
            String rawBody = dispatch.getTextBody();
            String preview = rawBody != null
                    ? rawBody.substring(0, Math.min(BODY_PREVIEW_MAX, rawBody.length()))
                    : null;
            String mailboxName = resolveMailboxName(dispatch.getMailboxId(), mailboxNameCache);
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
                    0,
                    // Phase 1 fields
                    null,
                    dispatch.getSourceIngressEventId(),
                    dispatch.getMailboxId(),
                    mailboxName,
                    dispatch.getFailureReason(),
                    dispatch.getResolvedToAddress(),
                    "OUTBOUND_DISPATCH",
                    String.valueOf(dispatch.getId()),
                    false,
                    preview != null && !preview.isBlank()
            ));
        });

        items.sort(Comparator.comparing(EmailThreadItem::timestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ResponseEntity.ok(items);
    }

    /**
     * Detail view for a specific outbound dispatch.
     * Verifies the dispatch belongs to the path ticketId.
     */
    /** Resolves mailbox display name, caching results to avoid repeated DB lookups. */
    private String resolveMailboxName(Long mailboxId, Map<Long, String> cache) {
        if (mailboxId == null) return null;
        return cache.computeIfAbsent(mailboxId, id -> {
            try {
                com.caseflow.email.domain.EmailMailbox mb = mailboxService.getById(id);
                return mb.getDisplayName() != null ? mb.getDisplayName() : mb.getName();
            } catch (Exception e) {
                log.warn("Mailbox {} not found while building thread item", id);
                return null;
            }
        });
    }

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

    @GetMapping("/dispatches")
    @PreAuthorize("@ticketAuth.canViewTicketEmail(authentication, #ticketId)")
    public ResponseEntity<List<DispatchResponse>> getDispatches(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/email/dispatches (list)", ticketId);
        return ResponseEntity.ok(
                dispatchMapper.toResponseList(dispatchService.findByTicketId(ticketId)));
    }

    /**
     * Retries a failed outbound dispatch.
     *
     * <p>Resets the dispatch to PENDING so the next scheduler tick will attempt delivery.
     * Only FAILED or PERMANENTLY_FAILED dispatches may be retried.
     */
    @PostMapping("/outbound/{dispatchId}/retry")
    @PreAuthorize("@ticketAuth.canSendTicketEmailReply(authentication, #ticketId)")
    public ResponseEntity<DispatchResponse> retryDispatch(@PathVariable Long ticketId,
                                                          @PathVariable Long dispatchId) {
        log.info("POST /tickets/{}/email/outbound/{}/retry", ticketId, dispatchId);
        OutboundEmailDispatch dispatch = dispatchService.getById(dispatchId);
        if (!ticketId.equals(dispatch.getTicketId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Outbound dispatch " + dispatchId + " not found under ticket " + ticketId);
        }
        dispatchService.retryDispatch(dispatch);
        return ResponseEntity.ok(dispatchMapper.toResponse(dispatchService.getById(dispatchId)));
    }

    /**
     * Cancels a pending outbound dispatch.
     *
     * <p>Only PENDING dispatches may be canceled. Typically used to cancel a scheduled send
     * before it fires.
     */
    @PostMapping("/outbound/{dispatchId}/cancel")
    @PreAuthorize("@ticketAuth.canSendTicketEmailReply(authentication, #ticketId)")
    public ResponseEntity<DispatchResponse> cancelDispatch(@PathVariable Long ticketId,
                                                           @PathVariable Long dispatchId) {
        log.info("POST /tickets/{}/email/outbound/{}/cancel", ticketId, dispatchId);
        OutboundEmailDispatch dispatch = dispatchService.getById(dispatchId);
        if (!ticketId.equals(dispatch.getTicketId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Outbound dispatch " + dispatchId + " not found under ticket " + ticketId);
        }
        dispatchService.markCanceled(dispatch);
        return ResponseEntity.ok(dispatchMapper.toResponse(dispatchService.getById(dispatchId)));
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
                principal.getUserId(),
                request.templateId(),
                request.templateCode(),
                Boolean.TRUE.equals(request.contentWasEdited())
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ReplyEnqueuedResponse(
                dispatch.getId(),
                request.sourceEventId(),
                dispatch.getResolvedToAddress(),
                fromAddress,
                dispatch.getMailboxId(),
                request.subject(),
                Instant.now()
        ));
    }
}
