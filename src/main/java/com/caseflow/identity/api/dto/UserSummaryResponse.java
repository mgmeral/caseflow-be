package com.caseflow.identity.api.dto;

public record UserSummaryResponse(
        Long id,
        String username,
        String fullName,
        Long roleId,
        String roleCode,
        Boolean isActive
) {}
