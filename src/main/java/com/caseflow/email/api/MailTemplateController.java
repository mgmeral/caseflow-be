package com.caseflow.email.api;

import com.caseflow.email.api.dto.MailTemplatePreviewRequest;
import com.caseflow.email.api.dto.MailTemplatePreviewResponse;
import com.caseflow.email.api.dto.MailTemplateRequest;
import com.caseflow.email.api.dto.MailTemplateResponse;
import com.caseflow.email.service.MailTemplateService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin — Mail Templates", description = "Manage outbound email templates")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/mail-templates")
public class MailTemplateController {

    private final MailTemplateService templateService;

    public MailTemplateController(MailTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public List<MailTemplateResponse> list() {
        return templateService.findAll().stream()
                .map(MailTemplateResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public MailTemplateResponse get(@PathVariable Long id) {
        return MailTemplateResponse.from(templateService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<MailTemplateResponse> create(@Valid @RequestBody MailTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MailTemplateResponse.from(templateService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public MailTemplateResponse update(@PathVariable Long id,
                                       @Valid @RequestBody MailTemplateRequest request) {
        return MailTemplateResponse.from(templateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public MailTemplatePreviewResponse preview(@PathVariable Long id,
                                               @RequestBody MailTemplatePreviewRequest request) {
        return templateService.preview(id, request);
    }
}
