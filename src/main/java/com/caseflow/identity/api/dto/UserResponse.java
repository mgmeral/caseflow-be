package com.caseflow.identity.api.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Boolean isActive,
        Instant createdAt,
        Instant lastLoginAt
) {}
