package com.caseflow.email.api;

import com.caseflow.email.api.dto.MailboxRequest;
import com.caseflow.email.api.dto.MailboxResponse;
import com.caseflow.email.api.mapper.EmailMailboxMapper;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.service.EmailMailboxService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin — Mailboxes", description = "Email mailbox management")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/mailboxes")
public class MailboxController {

    private static final Logger log = LoggerFactory.getLogger(MailboxController.class);

    private final EmailMailboxService mailboxService;
    private final EmailMailboxMapper mailboxMapper;

    public MailboxController(EmailMailboxService mailboxService, EmailMailboxMapper mailboxMapper) {
        this.mailboxService = mailboxService;
        this.mailboxMapper = mailboxMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<MailboxResponse> create(@Valid @RequestBody MailboxRequest request) {
        log.info("POST /admin/mailboxes — address: '{}'", request.address());
        EmailMailbox created = mailboxService.create(mailboxMapper.toEntity(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mailboxMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<MailboxResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody MailboxRequest request) {
        log.info("PUT /admin/mailboxes/{}", id);
        EmailMailbox updated = mailboxService.update(id, mailboxMapper.toEntity(request));
        return ResponseEntity.ok(mailboxMapper.toResponse(updated));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<MailboxResponse> activate(@PathVariable Long id) {
        log.info("PATCH /admin/mailboxes/{}/activate", id);
        return ResponseEntity.ok(mailboxMapper.toResponse(mailboxService.activate(id)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<MailboxResponse> deactivate(@PathVariable Long id) {
        log.info("PATCH /admin/mailboxes/{}/deactivate", id);
        return ResponseEntity.ok(mailboxMapper.toResponse(mailboxService.deactivate(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /admin/mailboxes/{}", id);
        mailboxService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<MailboxResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(mailboxMapper.toResponse(mailboxService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<List<MailboxResponse>> list() {
        return ResponseEntity.ok(mailboxMapper.toResponseList(mailboxService.findAll()));
    }
}
