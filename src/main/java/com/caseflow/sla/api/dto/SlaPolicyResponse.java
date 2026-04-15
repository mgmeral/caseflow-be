package com.caseflow.sla.api.dto;

import com.caseflow.sla.domain.SlaScope;

import java.time.Instant;

public record SlaPolicyResponse(
        Long id,
        String name,
        SlaScope scope,
        String priority,
        Long groupId,
        int firstResponseTargetMinutes,
        int resolutionTargetMinutes,
        int warningBeforeBreachMinutes,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
