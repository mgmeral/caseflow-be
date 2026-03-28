package com.caseflow.identity.api.dto;

import java.time.Instant;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Long roleId,
        String roleCode,
        String roleName,
        Boolean isActive,
        List<Long> groupIds,
        List<String> groupNames,
        Instant createdAt,
        Instant lastLoginAt
) {}
