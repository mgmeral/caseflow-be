package com.caseflow.automation.api.dto;

import com.caseflow.automation.domain.AutomationTriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAutomationRuleRequest(

        @NotBlank @Size(max = 200)
        String name,

        @Size(max = 1000)
        String description,

        @NotNull
        AutomationTriggerType triggerType,

        String conditionJson,
        String actionJson,

        @NotNull
        Boolean isActive,

        int executionOrder
) {}
