package com.caseflow.identity.api.dto;

import com.caseflow.identity.domain.GroupType;

public record GroupSummaryResponse(
        Long id,
        String name,
        GroupType type,
        Boolean isActive
) {}
