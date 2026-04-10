package com.caseflow.identity.api.dto;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String displayName,
        String firstName,
        String lastName,
        String locale,
        String avatarUrl,
        Long roleId,
        String roleCode,
        String roleName,
        List<Long> groupIds,
        List<String> groupNames,
        Boolean isActive,
        Instant createdAt,
        Instant lastLoginAt
) {}
