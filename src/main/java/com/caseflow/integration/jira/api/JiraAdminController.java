package com.caseflow.integration.jira.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.integration.jira.api.dto.JiraConfigRequest;
import com.caseflow.integration.jira.api.dto.JiraConfigResponse;
import com.caseflow.integration.jira.api.dto.JiraTestResponse;
import com.caseflow.integration.jira.domain.JiraConfig;
import com.caseflow.integration.jira.service.JiraConfigService;
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

    /**
     * Checks credentials, project and issue type step by step. With a body the given (unsaved)
     * form values are tested; without one, the saved configuration. Nothing is persisted.
     */
    @PostMapping("/test")
    public ResponseEntity<JiraTestResponse> testConnection(@Valid @RequestBody(required = false) JiraConfigRequest draft) {
        try {
            if (draft == null) {
                JiraConfig saved = configService.findConfig()
                        .orElseThrow(() -> new IllegalStateException("Jira integration is not configured"));
                return ResponseEntity.ok(JiraTestResponse.from(configService.diagnose(
                        saved.getBaseUrl(), saved.getUsername(), null, saved.getProjectKey(), saved.getIssueType())));
            }
            return ResponseEntity.ok(JiraTestResponse.from(configService.diagnose(
                    draft.baseUrl(), draft.username(), draft.apiToken(), draft.projectKey(), draft.issueType())));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(JiraTestResponse.failure(e.getMessage()));
        }
    }
}
