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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin — Mail Templates", description = "Manage outbound email templates")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/mail-templates")
public class MailTemplateController {

    private final MailTemplateService templateService;

    public MailTemplateController(MailTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Lists all templates. Supports optional filtering for the agent macro picker.
     *
     * @param usageType  optional usage type filter (e.g. CUSTOMER_REPLY, FOLLOW_UP)
     * @param activeOnly when true, only active templates are returned (default false)
     * @param search     optional text search on name, code, or subject
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public List<MailTemplateResponse> list(
            @RequestParam(required = false) String usageType,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String search) {
        return templateService.search(usageType, activeOnly, search).stream()
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

    /**
     * Returns the supported placeholder tokens and valid usage types for template authoring.
     * The FE can use this to display an inline helper panel.
     */
    @GetMapping("/help")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public Map<String, Object> help() {
        return Map.of(
                "supportedPlaceholders", List.of(
                        Map.of("name", "{replyBody}",
                               "description", "The agent's reply text — HTML-escaped when embedded in HTML templates, plain otherwise"),
                        Map.of("name", "{ticketRef}",
                               "description", "Ticket reference number, e.g. TKT-00001"),
                        Map.of("name", "{mailboxName}",
                               "description", "Display name of the sending mailbox"),
                        Map.of("name", "{agentName}",
                               "description", "Display name of the agent sending the reply"),
                        Map.of("name", "{signatureBlock}",
                               "description", "Optional agent signature block")
                ),
                "usageTypes", List.of(
                        Map.of("value", "CUSTOMER_REPLY",   "label", "Direct reply to a customer inquiry", "customerVisible", true),
                        Map.of("value", "ACKNOWLEDGEMENT",  "label", "Acknowledge receipt of a new ticket", "customerVisible", true),
                        Map.of("value", "FOLLOW_UP",        "label", "Follow up when awaiting customer response", "customerVisible", true),
                        Map.of("value", "RESOLUTION",       "label", "Notify customer the ticket has been resolved", "customerVisible", true),
                        Map.of("value", "NEED_MORE_INFO",   "label", "Request additional information from customer", "customerVisible", true),
                        Map.of("value", "INTERNAL_UPDATE",  "label", "Internal team update — not sent to customer", "customerVisible", false)
                )
        );
    }
}
