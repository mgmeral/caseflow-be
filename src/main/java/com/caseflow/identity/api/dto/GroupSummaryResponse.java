package com.caseflow.identity.api.dto;

import java.util.List;

public record GroupSummaryResponse(
        Long id,
        String name,
        Long groupTypeId,
        String groupTypeCode,
        String groupTypeName,
        Boolean isActive,
        int memberCount,
        List<Long> memberIds
) {}
