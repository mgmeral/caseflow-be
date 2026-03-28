package com.caseflow.identity.api.dto;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        Boolean isActive,
        List<Long> groupIds,
        List<String> groupNames,
        Instant createdAt,
        Instant lastLoginAt
) {}
