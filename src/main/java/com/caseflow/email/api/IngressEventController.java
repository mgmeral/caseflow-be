package com.caseflow.email.api;

import com.caseflow.common.api.PagedResponse;
import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.dto.QuarantineRequest;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.EmailIngressEventQueryService;
import com.caseflow.email.service.EmailIngressService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — Ingress Events", description = "Email ingress event monitoring and replay")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/ingress-events")
public class IngressEventController {

    private static final Logger log = LoggerFactory.getLogger(IngressEventController.class);

    private final EmailIngressEventQueryService queryService;
    private final EmailIngressService ingressService;
    private final IngressEventMapper mapper;

    public IngressEventController(EmailIngressEventQueryService queryService,
                                   EmailIngressService ingressService,
                                   IngressEventMapper mapper) {
        this.queryService = queryService;
        this.ingressService = ingressService;
        this.mapper = mapper;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_VIEW')")
    public ResponseEntity<IngressEventResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(queryService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_VIEW')")
    public ResponseEntity<PagedResponse<IngressEventResponse>> list(
            @RequestParam(required = false) IngressEventStatus status,
            @RequestParam(required = false) Long mailboxId,
            @RequestParam(required = false) Long ticketId,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EmailIngressEvent> page = queryService.searchPaged(status, mailboxId, ticketId, pageable);
        return ResponseEntity.ok(PagedResponse.from(page.map(mapper::toResponse)));
    }

    /**
     * Manually trigger Stage-2 processing for a FAILED event.
     */
    @PostMapping("/{id}/process")
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_MANAGE')")
    public ResponseEntity<IngressEventResponse> replay(@PathVariable Long id) {
        log.info("Manual replay requested — eventId: {}", id);
        ingressService.processEvent(id);
        return ResponseEntity.ok(mapper.toResponse(queryService.getById(id)));
    }

    /**
     * Manually quarantine an event to prevent automatic processing.
     */
    @PostMapping("/{id}/quarantine")
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_MANAGE')")
    public ResponseEntity<IngressEventResponse> quarantine(@PathVariable Long id,
                                                            @Valid @RequestBody QuarantineRequest request) {
        log.info("Manual quarantine requested — eventId: {}, reason: {}", id, request.reason());
        ingressService.quarantineEvent(id, request.reason());
        return ResponseEntity.ok(mapper.toResponse(queryService.getById(id)));
    }

    /**
     * Release a quarantined event back to RECEIVED for retry scheduler processing.
     */
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAuthority('PERM_EMAIL_OPERATIONS_MANAGE')")
    public ResponseEntity<IngressEventResponse> release(@PathVariable Long id) {
        log.info("Release from quarantine requested — eventId: {}", id);
        ingressService.releaseEvent(id);
        return ResponseEntity.ok(mapper.toResponse(queryService.getById(id)));
    }
}
