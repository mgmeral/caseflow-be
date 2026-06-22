package com.caseflow.automation.api;

import com.caseflow.automation.api.dto.AutomationRuleResponse;
import com.caseflow.automation.api.dto.CreateAutomationRuleRequest;
import com.caseflow.automation.api.dto.UpdateAutomationRuleRequest;
import com.caseflow.automation.domain.AutomationRule;
import com.caseflow.automation.domain.AutomationTriggerType;
import com.caseflow.automation.repository.AutomationRuleRepository;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** CRUD management API for automation rules. */
@Tag(name = "Automation Rules", description = "Management view for automation rule catalog")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin/automation/rules")
public class AutomationRuleController {

    private static final Logger log = LoggerFactory.getLogger(AutomationRuleController.class);

    private final AutomationRuleRepository ruleRepository;

    public AutomationRuleController(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    /**
     * Lists all automation rules, ordered by trigger type then execution order.
     *
     * @param triggerType optional filter — returns only rules for this trigger type
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<List<AutomationRuleResponse>> list(
            @RequestParam(required = false) AutomationTriggerType triggerType) {
        log.info("GET /admin/automation/rules — triggerType: {}", triggerType);
        var rules = triggerType != null
                ? ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(triggerType)
                : ruleRepository.findAllByOrderByTriggerTypeAscExecutionOrderAsc();
        return ResponseEntity.ok(rules.stream().map(AutomationRuleResponse::from).toList());
    }

    /**
     * Returns the available trigger types and a brief description of each,
     * for use by FE rule builder UIs.
     */
    @GetMapping("/meta/triggers")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<Map<String, String>> triggerTypes() {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (AutomationTriggerType t : AutomationTriggerType.values()) {
            result.put(t.name(), t.name().replace('_', ' ').toLowerCase());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<AutomationRuleResponse> create(
            @Valid @RequestBody CreateAutomationRuleRequest request) {
        log.info("POST /admin/automation/rules — name: '{}', trigger: {}", request.name(), request.triggerType());
        AutomationRule rule = new AutomationRule();
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setTriggerType(request.triggerType());
        rule.setConditionJson(request.conditionJson());
        rule.setActionJson(request.actionJson());
        rule.setExecutionOrder(request.executionOrder());
        rule.setActive(false);
        AutomationRule saved = ruleRepository.save(rule);
        log.info("Automation rule created — id: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(AutomationRuleResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<AutomationRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAutomationRuleRequest request) {
        log.info("PUT /admin/automation/rules/{} — isActive: {}", id, request.isActive());
        AutomationRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Automation rule " + id + " not found"));
        rule.setName(request.name());
        rule.setDescription(request.description());
        rule.setTriggerType(request.triggerType());
        rule.setConditionJson(request.conditionJson());
        rule.setActionJson(request.actionJson());
        rule.setActive(request.isActive());
        rule.setExecutionOrder(request.executionOrder());
        return ResponseEntity.ok(AutomationRuleResponse.from(ruleRepository.save(rule)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SETTINGS_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("DELETE /admin/automation/rules/{}", id);
        if (!ruleRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation rule " + id + " not found");
        }
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
