package com.caseflow.integration.notification.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.integration.notification.api.dto.ChannelConfigRequest;
import com.caseflow.integration.notification.api.dto.ChannelConfigResponse;
import com.caseflow.integration.notification.domain.NotificationChannelConfig;
import com.caseflow.integration.notification.service.NotificationChannelConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin endpoints for managing Slack/Teams notification channel configs.
 * Requires {@code PERM_INTEGRATION_CONFIG_MANAGE}.
 */
@RestController
@RequestMapping("/api/admin/integrations/channels")
@PreAuthorize("hasAuthority('PERM_INTEGRATION_CONFIG_MANAGE')")
public class NotificationChannelConfigController {

    private final NotificationChannelConfigService configService;

    public NotificationChannelConfigController(NotificationChannelConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<List<ChannelConfigResponse>> listAll() {
        List<ChannelConfigResponse> result = configService.findAll()
                .stream().map(ChannelConfigResponse::from).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChannelConfigResponse> getById(@PathVariable Long id) {
        NotificationChannelConfig config = configService.findById(id);
        return ResponseEntity.ok(ChannelConfigResponse.from(config));
    }

    @PostMapping
    public ResponseEntity<ChannelConfigResponse> create(
            @Valid @RequestBody ChannelConfigRequest req,
            @AuthenticationPrincipal CaseFlowUserDetails user) {

        if (req.webhookUrl() == null || req.webhookUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        NotificationChannelConfig config = configService.create(
                req.name(), req.channelType(), req.webhookUrl(),
                req.subscribedEvents(), req.scopeType(), req.scopeId(),
                req.enabled(), user.getUserId()
        );
        return ResponseEntity.status(201).body(ChannelConfigResponse.from(config));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChannelConfigResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ChannelConfigRequest req) {

        NotificationChannelConfig config = configService.update(
                id, req.name(), req.channelType(), req.webhookUrl(),
                req.subscribedEvents(), req.scopeType(), req.scopeId(), req.enabled()
        );
        return ResponseEntity.ok(ChannelConfigResponse.from(config));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
