package com.caseflow.email.api;

import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.service.EmailIngressEventQueryService;
import com.caseflow.email.service.EmailIngressService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin — Ingress Events", description = "Email ingress event monitoring and replay (admin only)")
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
    @PreAuthorize("hasAuthority('PERM_ADMIN_CONFIG')")
    public ResponseEntity<IngressEventResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toResponse(queryService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ADMIN_CONFIG')")
    public ResponseEntity<List<IngressEventResponse>> list(
            @RequestParam(required = false) IngressEventStatus status) {
        List<IngressEventResponse> result = status != null
                ? mapper.toResponseList(queryService.findByStatus(status))
                : mapper.toResponseList(queryService.findAll());
        return ResponseEntity.ok(result);
    }

    /**
     * Manually trigger Stage-2 processing for a specific event.
     * Useful for replaying FAILED or QUARANTINED events after remediation.
     */
    @PostMapping("/{id}/process")
    @PreAuthorize("hasAuthority('PERM_ADMIN_CONFIG')")
    public ResponseEntity<IngressEventResponse> replay(@PathVariable Long id) {
        log.info("Manual replay requested — eventId: {}", id);
        ingressService.processEvent(id);
        return ResponseEntity.ok(mapper.toResponse(queryService.getById(id)));
    }
}
