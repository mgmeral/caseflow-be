package com.caseflow.automation.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One condition clause from {@code conditionJson}.
 * Example: {@code {"field":"priority","op":"eq","value":"HIGH"}}
 * <p>Supported fields: priority, status, assignedGroupId, assignedUserId, customerId.
 * Supported ops: eq, neq, not_null, is_null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutomationCondition(String field, String op, String value) {}
