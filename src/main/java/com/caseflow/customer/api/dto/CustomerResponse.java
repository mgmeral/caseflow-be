package com.caseflow.customer.api.dto;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String name,
        String code,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
