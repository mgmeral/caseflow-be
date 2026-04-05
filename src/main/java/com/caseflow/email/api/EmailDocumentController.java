package com.caseflow.email.api;

import com.caseflow.email.api.dto.EmailDocumentRawResponse;
import com.caseflow.email.api.dto.EmailDocumentResponse;
import com.caseflow.email.api.dto.EmailDocumentSummaryResponse;
import com.caseflow.email.api.dto.IngestEmailRequest;
import com.caseflow.email.api.mapper.EmailDocumentMapper;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.service.EmailDocumentQueryService;
import com.caseflow.email.service.EmailIngressService;
import com.caseflow.email.service.IngressEmailData;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Emails", description = "Email document queries and webhook ingest")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/emails")
public class EmailDocumentController {

    private static final Logger log = LoggerFactory.getLogger(EmailDocumentController.class);

    private final EmailDocumentQueryService emailDocumentQueryService;
    private final EmailDocumentMapper emailDocumentMapper;
    private final EmailIngressService emailIngressService;
    private final AttachmentService attachmentService;
    private final AttachmentMetadataMapper attachmentMetadataMapper;

    public EmailDocumentController(EmailDocumentQueryService emailDocumentQueryService,
                                   EmailDocumentMapper emailDocumentMapper,
                                   EmailIngressService emailIngressService,
                                   AttachmentService attachmentService,
                                   AttachmentMetadataMapper attachmentMetadataMapper) {
        this.emailDocumentQueryService = emailDocumentQueryService;
        this.emailDocumentMapper = emailDocumentMapper;
        this.emailIngressService = emailIngressService;
        this.attachmentService = attachmentService;
        this.attachmentMetadataMapper = attachmentMetadataMapper;
    }

    /**
     * Stage-1 webhook ingest: stores the inbound email as a durable event (RECEIVED) and returns 202.
     *
     * <p>All threading headers (In-Reply-To, References) and body content are persisted in the event
     * so Stage-2 can perform correct thread resolution and ticket linkage asynchronously.
     *
     * <p>Stage-2 processing (threading, customer routing, ticket creation) happens via the retry
     * scheduler or can be triggered manually via POST /api/admin/ingress-events/{id}/process.
     *
     * <p>Idempotent: duplicate messageIds return the existing event (HTTP 202).
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_MANAGE')")
    public ResponseEntity<Void> ingest(@Valid @RequestBody IngestEmailRequest request) {
        log.info("POST /emails/ingest — messageId: '{}', from: '{}', subject: '{}'",
                request.messageId(), request.from(), request.subject());

        IngressEmailData data = new IngressEmailData(
                request.messageId(),
                request.from(),
                request.to() != null ? String.join(", ", request.to()) : null,
                request.inReplyTo(),
                request.references(),
                null,   // replyTo — not in IngestEmailRequest; use From for reply targeting
                request.cc() != null ? String.join(", ", request.cc()) : null,
                request.subject(),
                request.textBody(),
                request.htmlBody(),
                request.mailboxId(),
                request.receivedAt(),
                request.envelopeRecipient(),
                null    // attachments — webhook provider handles binary storage externally
        );

        emailIngressService.receiveEvent(data);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_TICKET_READ')")
    public ResponseEntity<EmailDocumentResponse> getById(@PathVariable String id) {
        log.info("GET /emails/{}", id);
        return emailDocumentQueryService.findById(id)
                .map(doc -> ResponseEntity.ok(buildDetailResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    private EmailDocumentResponse buildDetailResponse(EmailDocument doc) {
        List<AttachmentMetadataResponse> attachments =
                attachmentMetadataMapper.toResponseList(attachmentService.findByEmailId(doc.getId()));
        EmailDocumentResponse base = emailDocumentMapper.toResponse(doc);
        return new EmailDocumentResponse(
                base.id(), base.messageId(), base.threadKey(), base.subject(),
                base.from(), base.to(), base.cc(),
                base.receivedAt(), base.parsedAt(), base.ticketId(),
                base.textBody(), base.sanitizedHtmlBody(),
                attachments);
    }

    /**
     * Raw source view — returns the original unsanitized HTML body for audit and debugging.
     * Restricted to email operations admins. Never use this as the operator-facing default.
     */
    @GetMapping("/{id}/raw")
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_MANAGE')")
    public ResponseEntity<EmailDocumentRawResponse> getRawById(@PathVariable String id) {
        log.info("GET /emails/{}/raw", id);
        return emailDocumentQueryService.findById(id)
                .map(doc -> ResponseEntity.ok(emailDocumentMapper.toRawResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-ticket/{ticketId}")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<List<EmailDocumentSummaryResponse>> getByTicket(@PathVariable Long ticketId) {
        log.info("GET /emails/by-ticket/{}", ticketId);
        return ResponseEntity.ok(
                emailDocumentMapper.toSummaryResponseList(
                        emailDocumentQueryService.findByTicketId(ticketId)));
    }

    @GetMapping("/by-thread/{threadKey}")
    @PreAuthorize("hasAuthority('PERM_TICKET_READ')")
    public ResponseEntity<List<EmailDocumentSummaryResponse>> getByThread(@PathVariable String threadKey) {
        log.info("GET /emails/by-thread/{}", threadKey);
        return ResponseEntity.ok(
                emailDocumentMapper.toSummaryResponseList(
                        emailDocumentQueryService.findByThreadKey(threadKey)));
    }
}
