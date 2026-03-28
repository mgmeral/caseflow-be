package com.caseflow.email.api;

import com.caseflow.email.api.dto.EmailDocumentResponse;
import com.caseflow.email.api.dto.EmailDocumentSummaryResponse;
import com.caseflow.email.api.dto.IngestEmailRequest;
import com.caseflow.email.api.mapper.EmailDocumentMapper;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.service.EmailDocumentQueryService;
import com.caseflow.email.service.EmailProcessingService;
import com.caseflow.email.service.ParsedEmail;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Emails", description = "Email document queries and ingest")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/emails")
public class EmailDocumentController {

    private static final Logger log = LoggerFactory.getLogger(EmailDocumentController.class);

    private final EmailDocumentQueryService emailDocumentQueryService;
    private final EmailDocumentMapper emailDocumentMapper;
    private final EmailProcessingService emailProcessingService;

    public EmailDocumentController(EmailDocumentQueryService emailDocumentQueryService,
                                   EmailDocumentMapper emailDocumentMapper,
                                   EmailProcessingService emailProcessingService) {
        this.emailDocumentQueryService = emailDocumentQueryService;
        this.emailDocumentMapper = emailDocumentMapper;
        this.emailProcessingService = emailProcessingService;
    }

    /**
     * Manually ingest a parsed inbound email.
     * Idempotent: returns 409 if messageId already exists.
     */
    @PostMapping("/ingest")
    public ResponseEntity<EmailDocumentResponse> ingest(@Valid @RequestBody IngestEmailRequest request) {
        log.info("POST /emails/ingest — messageId: '{}', from: '{}', subject: '{}'",
                request.messageId(), request.from(), request.subject());
        ParsedEmail parsed = new ParsedEmail();
        parsed.setMessageId(request.messageId());
        parsed.setInReplyTo(request.inReplyTo());
        parsed.setReferences(request.references());
        parsed.setSubject(request.subject());
        parsed.setNormalizedSubject(normalizeSubject(request.subject()));
        parsed.setFrom(request.from());
        parsed.setTo(request.to() != null ? request.to() : List.of());
        parsed.setCc(request.cc() != null ? request.cc() : List.of());
        parsed.setBcc(List.of());
        parsed.setTextBody(request.textBody());
        parsed.setHtmlBody(request.htmlBody());
        parsed.setReceivedAt(request.receivedAt());

        EmailDocument doc = emailProcessingService.process(parsed);
        log.info("POST /emails/ingest succeeded — docId: '{}', ticketId: {}", doc.getId(), doc.getTicketId());
        return ResponseEntity.status(HttpStatus.CREATED).body(emailDocumentMapper.toResponse(doc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailDocumentResponse> getById(@PathVariable String id) {
        log.info("GET /emails/{}", id);
        return emailDocumentQueryService.findById(id)
                .map(doc -> ResponseEntity.ok(emailDocumentMapper.toResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<List<EmailDocumentSummaryResponse>> getByTicket(@PathVariable Long ticketId) {
        log.info("GET /emails/by-ticket/{}", ticketId);
        return ResponseEntity.ok(
                emailDocumentMapper.toSummaryResponseList(
                        emailDocumentQueryService.findByTicketId(ticketId)));
    }

    @GetMapping("/by-thread/{threadKey}")
    public ResponseEntity<List<EmailDocumentSummaryResponse>> getByThread(@PathVariable String threadKey) {
        log.info("GET /emails/by-thread/{}", threadKey);
        return ResponseEntity.ok(
                emailDocumentMapper.toSummaryResponseList(
                        emailDocumentQueryService.findByThreadKey(threadKey)));
    }

    private String normalizeSubject(String subject) {
        if (subject == null) return null;
        return subject.replaceAll("(?i)^(re:|fwd?:|aw:)\\s*", "").trim();
    }
}
