package com.caseflow.automation.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One action entry from {@code actionJson}.
 * Example: {@code {"type":"ASSIGN_GROUP","groupId":5}}
 * <p>Supported types: SET_PRIORITY, SET_STATUS, ASSIGN_GROUP, ASSIGN_USER.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AutomationAction(
        String type,
        String priority,
        String status,
        Long groupId,
        Long userId
) {}
