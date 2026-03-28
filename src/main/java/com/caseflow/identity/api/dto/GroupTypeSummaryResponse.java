package com.caseflow.identity.api.dto;

public record GroupTypeSummaryResponse(
        Long id,
        String code,
        String name,
        Boolean isActive
) {}
