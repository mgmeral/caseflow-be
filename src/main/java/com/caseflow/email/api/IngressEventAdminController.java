package com.caseflow.email.api;

import com.caseflow.email.api.dto.IngressEventAdminResponse;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.IngressEventAdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Admin — Ingress Events", description = "Operator recovery operations for inbound ingress events")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/ingress-events")
public class IngressEventAdminController {

    private static final Logger log = LoggerFactory.getLogger(IngressEventAdminController.class);

    private final IngressEventAdminService adminService;

    public IngressEventAdminController(IngressEventAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<Page<IngressEventAdminResponse>> list(
            @RequestParam(required = false) IngressEventStatus status,
            @RequestParam(required = false) Long mailboxId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) Long ticketId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 25, sort = "receivedAt") Pageable pageable) {
        Page<IngressEventAdminResponse> page = adminService
                .findFiltered(status, mailboxId, messageId, ticketId, from, to, pageable)
                .map(IngressEventAdminResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<IngressEventAdminResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(IngressEventAdminResponse.from(adminService.getById(id)));
    }

    /**
     * Manually triggers Stage-2 processing for the event.
     * QUARANTINED events are released first, then processed.
     */
    @PostMapping("/{id}/process")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> process(@PathVariable Long id) {
        log.info("ADMIN POST /ingress-events/{}/process", id);
        adminService.processEvent(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resets a FAILED event to RECEIVED and triggers immediate processing.
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> retry(@PathVariable Long id) {
        log.info("ADMIN POST /ingress-events/{}/retry", id);
        adminService.retryEvent(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Moves an event to QUARANTINED with an optional reason.
     */
    @PostMapping("/{id}/quarantine")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> quarantine(@PathVariable Long id,
                                            @RequestParam(required = false) String reason) {
        log.info("ADMIN POST /ingress-events/{}/quarantine — reason: '{}'", id, reason);
        adminService.quarantineEvent(id, reason);
        return ResponseEntity.noContent().build();
    }

    /**
     * Releases a QUARANTINED event back to RECEIVED.
     */
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> release(@PathVariable Long id) {
        log.info("ADMIN POST /ingress-events/{}/release", id);
        adminService.releaseEvent(id);
        return ResponseEntity.noContent().build();
    }
}
