package com.caseflow.email.api;

import com.caseflow.email.api.dto.EmailDocumentResponse;
import com.caseflow.email.api.dto.EmailDocumentSummaryResponse;
import com.caseflow.email.api.mapper.EmailDocumentMapper;
import com.caseflow.email.service.EmailDocumentQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Emails", description = "Email document queries")
@RestController
@RequestMapping("/api/emails")
public class EmailDocumentController {

    private final EmailDocumentQueryService emailDocumentQueryService;
    private final EmailDocumentMapper emailDocumentMapper;

    public EmailDocumentController(EmailDocumentQueryService emailDocumentQueryService,
                                   EmailDocumentMapper emailDocumentMapper) {
        this.emailDocumentQueryService = emailDocumentQueryService;
        this.emailDocumentMapper = emailDocumentMapper;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailDocumentResponse> getById(@PathVariable String id) {
        return emailDocumentQueryService.findById(id)
                .map(doc -> ResponseEntity.ok(emailDocumentMapper.toResponse(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<List<EmailDocumentSummaryResponse>> getByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(
                emailDocumentMapper.toSummaryResponseList(
                        emailDocumentQueryService.findByTicketId(ticketId)));
    }

    @GetMapping("/by-thread/{threadKey}")
    public ResponseEntity<List<EmailDocumentSummaryResponse>> getByThread(@PathVariable String threadKey) {
        return ResponseEntity.ok(
                emailDocumentMapper.toSummaryResponseList(
                        emailDocumentQueryService.findByThreadKey(threadKey)));
    }
}
