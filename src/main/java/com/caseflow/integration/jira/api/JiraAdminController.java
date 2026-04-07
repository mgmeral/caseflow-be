package com.caseflow.integration.jira.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.integration.jira.api.dto.JiraConfigRequest;
import com.caseflow.integration.jira.api.dto.JiraConfigResponse;
import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.service.JiraConfigService;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Admin endpoints for managing the Jira integration configuration.
 * Requires {@code PERM_INTEGRATION_CONFIG_MANAGE}.
 */
@RestController
@RequestMapping("/api/admin/integrations/jira")
@PreAuthorize("hasAuthority('PERM_INTEGRATION_CONFIG_MANAGE')")
public class JiraAdminController {

    private final JiraConfigService configService;

    public JiraAdminController(JiraConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/config")
    public ResponseEntity<JiraConfigResponse> getConfig() {
        Optional<JiraConfig> config = configService.findConfig();
        return config
                .map(c -> ResponseEntity.ok(JiraConfigResponse.from(c)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PutMapping("/config")
    public ResponseEntity<JiraConfigResponse> saveConfig(
            @Valid @RequestBody JiraConfigRequest req,
            @AuthenticationPrincipal CaseFlowUserDetails user) {

        JiraConfig saved = configService.save(
                req.baseUrl(), req.authType(), req.username(), req.apiToken(),
                req.projectKey(), req.issueType(), req.defaultLabels(),
                req.appBaseUrl(), req.enabled(), user.getUserId()
        );
        return ResponseEntity.ok(JiraConfigResponse.from(saved));
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection() {
        try {
            boolean ok = configService.testConnection();
            return ResponseEntity.ok(Map.of("success", ok, "message", "Connection successful"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        } catch (IntegrationJobExecutionException e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
