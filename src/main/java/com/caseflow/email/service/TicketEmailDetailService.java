package com.caseflow.email.service;

import com.caseflow.common.exception.DispatchNotFoundException;
import com.caseflow.email.api.dto.EmailDetailType;
import com.caseflow.email.api.dto.UnifiedEmailDetailResponse;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.domain.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the unified email detail for a ticket-scoped detail request.
 *
 * <p>All access is ticket-scoped: callers must validate ownership before calling here.
 */
@Service
public class TicketEmailDetailService {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailDetailService.class);
    private static final int PREVIEW_MAX = 500;

    private final EmailDocumentQueryService docQueryService;
    private final OutboundEmailDispatchRepository dispatchRepository;
    private final EmailMailboxService mailboxService;
    private final MailTemplateService mailTemplateService;
    private final AttachmentService attachmentService;
    private final AttachmentMetadataMapper attachmentMetadataMapper;

    public TicketEmailDetailService(EmailDocumentQueryService docQueryService,
                                    OutboundEmailDispatchRepository dispatchRepository,
                                    EmailMailboxService mailboxService,
                                    MailTemplateService mailTemplateService,
                                    AttachmentService attachmentService,
                                    AttachmentMetadataMapper attachmentMetadataMapper) {
        this.docQueryService = docQueryService;
        this.dispatchRepository = dispatchRepository;
        this.mailboxService = mailboxService;
        this.mailTemplateService = mailTemplateService;
        this.attachmentService = attachmentService;
        this.attachmentMetadataMapper = attachmentMetadataMapper;
    }

    /**
     * Resolves a unified detail response for the given ticket, detail type, and detail id.
     *
     * @param ticket     the owning ticket (pre-validated by caller)
     * @param detailType EMAIL_DOCUMENT or OUTBOUND_DISPATCH
     * @param detailId   the id string from the thread item
     */
    @Transactional(readOnly = true)
    public UnifiedEmailDetailResponse resolve(Ticket ticket, String detailType, String detailId) {
        EmailDetailType type;
        try {
            type = EmailDetailType.valueOf(detailType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown detailType: " + detailType + ". Valid values: EMAIL_DOCUMENT, OUTBOUND_DISPATCH");
        }

        return switch (type) {
            case EMAIL_DOCUMENT -> resolveEmailDocument(ticket, detailId);
            case OUTBOUND_DISPATCH -> resolveOutboundDispatch(ticket, detailId);
        };
    }

    // ── Private: EMAIL_DOCUMENT ───────────────────────────────────────────────

    private UnifiedEmailDetailResponse resolveEmailDocument(Ticket ticket, String docId) {
        EmailDocument doc = docQueryService.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Email document not found: " + docId));

        // Ownership: the document must belong to this ticket
        if (!ticket.getId().equals(doc.getTicketId())) {
            log.warn("Ownership mismatch — docId: {} belongs to ticketId: {}, requested under ticketId: {}",
                    docId, doc.getTicketId(), ticket.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Email document " + docId + " not found under this ticket");
        }

        MailboxInfo mb = resolveMailbox(doc.getMailboxId());
        List<AttachmentMetadataResponse> attachments =
                attachmentMetadataMapper.toResponseList(attachmentService.findByEmailId(docId));

        String preview = doc.getBodyPreview();
        if (preview == null && doc.getTextBody() != null) {
            preview = doc.getTextBody().substring(0, Math.min(PREVIEW_MAX, doc.getTextBody().length()));
        }

        return new UnifiedEmailDetailResponse(
                EmailDetailType.EMAIL_DOCUMENT.name(),
                docId,
                ticket.getPublicId(),
                "INBOUND",
                mb.id(), mb.name(), mb.address(),
                doc.getMessageId(),
                doc.getInReplyTo(),
                doc.getFrom(),
                doc.getTo() != null ? String.join(", ", doc.getTo()) : null,
                doc.getCc(),
                doc.getBcc(),
                null,  // replyTo not stored in EmailDocument directly
                doc.getSubject(),
                "RECEIVED",  // EmailDocument is always a received inbound
                null,
                null,
                doc.getReceivedAt(),
                doc.getParsedAt(),
                doc.getTextBody(),
                doc.getSanitizedHtmlBody(),
                preview,
                attachments,
                null,  // no template for inbound
                null,  // no reply context for inbound
                null
        );
    }

    // ── Private: OUTBOUND_DISPATCH ────────────────────────────────────────────

    private UnifiedEmailDetailResponse resolveOutboundDispatch(Ticket ticket, String detailId) {
        long dispatchId;
        try {
            dispatchId = Long.parseLong(detailId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid OUTBOUND_DISPATCH detailId (must be numeric): " + detailId);
        }

        OutboundEmailDispatch dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Outbound dispatch not found: " + dispatchId));

        // Ownership
        if (!ticket.getId().equals(dispatch.getTicketId())) {
            log.warn("Ownership mismatch — dispatchId: {} belongs to ticketId: {}, requested under ticketId: {}",
                    dispatchId, dispatch.getTicketId(), ticket.getId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Outbound dispatch " + dispatchId + " not found under this ticket");
        }

        MailboxInfo mb = resolveMailbox(dispatch.getMailboxId());
        UnifiedEmailDetailResponse.TemplateInfo templateInfo = resolveTemplateInfo(dispatch);
        UnifiedEmailDetailResponse.ReplyContext replyContext = buildReplyContext(dispatch);

        String preview = dispatch.getTextBody() != null
                ? dispatch.getTextBody().substring(0, Math.min(PREVIEW_MAX, dispatch.getTextBody().length()))
                : null;

        return new UnifiedEmailDetailResponse(
                EmailDetailType.OUTBOUND_DISPATCH.name(),
                String.valueOf(dispatchId),
                ticket.getPublicId(),
                "OUTBOUND",
                mb.id(), mb.name(), mb.address(),
                dispatch.getMessageId(),
                dispatch.getInReplyToMessageId(),
                dispatch.getFromAddress(),
                dispatch.getToAddress(),
                null, null, null,  // cc, bcc, replyTo not tracked on dispatch
                dispatch.getSubject(),
                dispatch.getStatus().name(),
                dispatch.getFailureReason(),
                dispatch.getSentAt(),
                null,
                dispatch.getCreatedAt(),
                dispatch.getTextBody(),
                dispatch.getHtmlBody(),
                preview,
                List.of(),  // outbound attachments not tracked in dispatch
                templateInfo,
                replyContext,
                dispatch.getContentWasEdited()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private MailboxInfo resolveMailbox(Long mailboxId) {
        if (mailboxId == null) return new MailboxInfo(null, null, null);
        try {
            EmailMailbox mb = mailboxService.getById(mailboxId);
            String displayName = mb.getDisplayName() != null ? mb.getDisplayName() : mb.getName();
            return new MailboxInfo(mb.getId(), displayName, mb.getAddress());
        } catch (Exception e) {
            log.warn("Mailbox {} not found during detail resolution", mailboxId);
            return new MailboxInfo(mailboxId, null, null);
        }
    }

    private UnifiedEmailDetailResponse.TemplateInfo resolveTemplateInfo(OutboundEmailDispatch dispatch) {
        if (dispatch.getAppliedTemplateCode() == null) return null;
        String name = null;
        if (dispatch.getAppliedTemplateId() != null) {
            try {
                MailTemplate t = mailTemplateService.findById(dispatch.getAppliedTemplateId());
                name = t.getName();
            } catch (Exception e) {
                // Template may have been deleted — use code as fallback
            }
        }
        return new UnifiedEmailDetailResponse.TemplateInfo(
                dispatch.getAppliedTemplateId(),
                dispatch.getAppliedTemplateCode(),
                name);
    }

    private UnifiedEmailDetailResponse.ReplyContext buildReplyContext(OutboundEmailDispatch dispatch) {
        String replySourceType = dispatch.getSourceIngressEventId() != null
                ? "INBOUND_EVENT" : "MANUAL_OVERRIDE";
        return new UnifiedEmailDetailResponse.ReplyContext(
                dispatch.getResolvedToAddress(),
                replySourceType,
                dispatch.getSourceIngressEventId());
    }

    private record MailboxInfo(Long id, String name, String address) {}
}
