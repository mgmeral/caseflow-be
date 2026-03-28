package com.caseflow.identity.api.dto;

import java.time.Instant;
import java.util.List;

public record GroupResponse(
        Long id,
        String name,
        Long groupTypeId,
        String groupTypeCode,
        String groupTypeName,
        String description,
        Boolean isActive,
        Instant createdAt,
        int memberCount,
        List<UserSummaryResponse> members
) {}
