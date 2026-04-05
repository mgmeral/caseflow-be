package com.caseflow.email.api;

import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.service.TicketQueryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Exposes attachment metadata and content for a specific inbound email under a ticket.
 *
 * <p>All endpoints are scoped to the ticket's stable public UUID so callers
 * are not coupled to internal numeric IDs.
 *
 * <h2>Ownership model</h2>
 * The controller verifies that:
 * <ol>
 *   <li>The {@code ticketPublicId} identifies a real ticket the caller can view.</li>
 *   <li>The requested attachment belongs to that ticket (or returns 404).</li>
 * </ol>
 */
@Tag(name = "Ticket Email Attachments", description = "Attachment access for inbound emails")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketPublicId}/emails/{emailId}/attachments")
public class TicketEmailAttachmentController {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailAttachmentController.class);

    /** Content types that browsers can render inline — served with Content-Disposition: inline. */
    private static final Set<String> INLINE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain");

    private final AttachmentService attachmentService;
    private final AttachmentMetadataMapper attachmentMetadataMapper;
    private final TicketQueryService ticketQueryService;

    public TicketEmailAttachmentController(AttachmentService attachmentService,
                                           AttachmentMetadataMapper attachmentMetadataMapper,
                                           TicketQueryService ticketQueryService) {
        this.attachmentService = attachmentService;
        this.attachmentMetadataMapper = attachmentMetadataMapper;
        this.ticketQueryService = ticketQueryService;
    }

    /**
     * Lists all attachment metadata records for a specific email under this ticket.
     */
    @GetMapping
    @PreAuthorize("@ticketAuth.canViewTicketEmailByPublicId(authentication, #ticketPublicId)")
    public ResponseEntity<List<AttachmentMetadataResponse>> listAttachments(
            @PathVariable UUID ticketPublicId,
            @PathVariable String emailId) {
        log.info("GET /tickets/{}/emails/{}/attachments", ticketPublicId, emailId);
        Ticket ticket = ticketQueryService.getByPublicId(ticketPublicId);

        List<AttachmentMetadata> attachments = attachmentService.findByEmailId(emailId)
                .stream()
                .filter(a -> ticket.getId().equals(a.getTicketId()))
                .toList();

        return ResponseEntity.ok(attachmentMetadataMapper.toResponseList(attachments));
    }

    /**
     * Streams attachment content.
     *
     * <p>Ownership check: the attachment must belong to the specified ticket.
     * Returns 404 if the attachment ID does not exist or belongs to a different ticket.
     */
    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("@ticketAuth.canViewTicketEmailByPublicId(authentication, #ticketPublicId)")
    public ResponseEntity<InputStreamResource> getContent(
            @PathVariable UUID ticketPublicId,
            @PathVariable String emailId,
            @PathVariable Long attachmentId) {
        log.info("GET /tickets/{}/emails/{}/attachments/{}/content",
                ticketPublicId, emailId, attachmentId);

        Ticket ticket = ticketQueryService.getByPublicId(ticketPublicId);
        AttachmentMetadata metadata = attachmentService.getById(attachmentId);

        // Ownership: attachment must belong to this ticket AND this email
        if (!ticket.getId().equals(metadata.getTicketId())) {
            log.warn("Ownership mismatch — attachmentId: {} belongs to ticketId: {}, "
                    + "requested under ticketPublicId: {}", attachmentId, metadata.getTicketId(), ticketPublicId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Attachment " + attachmentId + " not found under ticket " + ticketPublicId);
        }
        if (emailId != null && !emailId.equals(metadata.getEmailId())) {
            log.warn("Ownership mismatch — attachmentId: {} belongs to emailId: {}, "
                    + "requested under emailId: {}", attachmentId, metadata.getEmailId(), emailId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Attachment " + attachmentId + " not found under email " + emailId);
        }

        log.info("Streaming attachment — attachmentId: {}, fileName: '{}'",
                attachmentId, metadata.getFileName());
        InputStream stream = attachmentService.download(metadata.getObjectKey());

        HttpHeaders headers = new HttpHeaders();
        String contentType = metadata.getContentType();
        headers.setContentType(MediaType.parseMediaType(contentType));
        boolean inline = INLINE_TYPES.contains(contentType.toLowerCase());
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(metadata.getFileName()).build()
                : ContentDisposition.attachment().filename(metadata.getFileName()).build();
        headers.setContentDisposition(disposition);
        headers.setContentLength(metadata.getSize());

        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));
    }
}
