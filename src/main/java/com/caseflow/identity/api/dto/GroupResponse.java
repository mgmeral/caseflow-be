package com.caseflow.identity.api.dto;

import com.caseflow.identity.domain.GroupType;

import java.time.Instant;

public record GroupResponse(
        Long id,
        String name,
        GroupType type,
        Boolean isActive,
        Instant createdAt
) {}
