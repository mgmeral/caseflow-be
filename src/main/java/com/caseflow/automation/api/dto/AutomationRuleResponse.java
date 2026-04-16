package com.caseflow.automation.api.dto;

import com.caseflow.automation.domain.AutomationRule;
import com.caseflow.automation.domain.AutomationTriggerType;

import java.time.Instant;

/** Read-only view of an automation rule returned by the management API. */
public record AutomationRuleResponse(
        Long id,
        String name,
        String description,
        AutomationTriggerType triggerType,
        String conditionJson,
        String actionJson,
        boolean isActive,
        int executionOrder,
        Instant createdAt,
        Instant updatedAt
) {

    public static AutomationRuleResponse from(AutomationRule rule) {
        return new AutomationRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getDescription(),
                rule.getTriggerType(),
                rule.getConditionJson(),
                rule.getActionJson(),
                rule.isActive(),
                rule.getExecutionOrder(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
