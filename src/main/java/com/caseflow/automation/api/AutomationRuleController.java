package com.caseflow.automation.api;

import com.caseflow.automation.api.dto.AutomationRuleResponse;
import com.caseflow.automation.domain.AutomationTriggerType;
import com.caseflow.automation.repository.AutomationRuleRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only management view for automation rules.
 *
 * <p>Rule execution is not yet implemented — this endpoint exists to let operators
 * inspect the rule catalog and prepare for activation. Write endpoints (create / update /
 * delete) will be added when the rule engine is implemented.
 */
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
}
