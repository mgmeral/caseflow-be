package com.caseflow.identity.api.dto;

public record UserSummaryResponse(
        Long id,
        String username,
        String fullName,
        String role,
        Boolean isActive
) {}
