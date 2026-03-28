package com.caseflow.identity.api.dto;

import java.time.Instant;

public record GroupTypeResponse(
        Long id,
        String code,
        String name,
        String description,
        Boolean isActive,
        Instant createdAt
) {}
