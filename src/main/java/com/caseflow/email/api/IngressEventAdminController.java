package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.common.api.PagedResponse;
import com.caseflow.email.api.dto.IngressEventAdminResponse;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.IngressEventAdminService;
import com.caseflow.email.service.IngressEventFilter;
import com.caseflow.security.audit.SecurityAuditService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin — Ingress Events", description = "Operator recovery operations for inbound ingress events")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/ingress-events")
public class IngressEventAdminController {

    private static final Logger log = LoggerFactory.getLogger(IngressEventAdminController.class);

    private static final String MANAGE_AUTHORITY = "PERM_EMAIL_CONFIG_MANAGE";

    private final IngressEventAdminService adminService;
    private final SecurityAuditService auditService;

    public IngressEventAdminController(IngressEventAdminService adminService,
                                       SecurityAuditService auditService) {
        this.adminService = adminService;
        this.auditService = auditService;
    }

    /**
     * Newest first by default. {@code status} may repeat ({@code ?status=FAILED&status=QUARANTINED}).
     * Sender, subject and failure text are always masked in the list.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<PagedResponse<IngressEventAdminResponse>> list(
            @RequestParam(required = false) List<IngressEventStatus> status,
            @RequestParam(required = false) Long mailboxId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) Long ticketId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        IngressEventFilter filter = new IngressEventFilter(status, mailboxId, messageId, ticketId, q, from, to);
        Page<IngressEventAdminResponse> page = adminService.findFiltered(filter, pageable)
                .map(IngressEventAdminResponse::masked);
        return ResponseEntity.ok(PagedResponse.from(page));
    }

    /**
     * Event count per status under the same non-status filters as the list; every status is present.
     */
    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<Map<IngressEventStatus, Long>> counts(
            @RequestParam(required = false) Long mailboxId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        IngressEventFilter filter = new IngressEventFilter(null, mailboxId, null, null, q, from, to);
        return ResponseEntity.ok(adminService.countByStatus(filter));
    }

    /**
     * Full values only for operators who may act on the event ({@code EMAIL_CONFIG_MANAGE});
     * every such view is written to the security audit log. Others get the masked record.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<IngressEventAdminResponse> getById(@PathVariable Long id,
                                                            Authentication authentication,
                                                            HttpServletRequest request) {
        EmailIngressEvent event = adminService.getById(id);
        if (!canSeeUnmasked(authentication)) {
            return ResponseEntity.ok(IngressEventAdminResponse.masked(event));
        }
        Long userId = authentication.getPrincipal() instanceof CaseFlowUserDetails user ? user.getUserId() : null;
        auditService.recordSensitiveDataView(userId, authentication.getName(), request.getRemoteAddr(),
                "INGRESS_EVENT:" + id);
        return ResponseEntity.ok(IngressEventAdminResponse.from(event));
    }

    private static boolean canSeeUnmasked(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> MANAGE_AUTHORITY.equals(a.getAuthority()));
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
