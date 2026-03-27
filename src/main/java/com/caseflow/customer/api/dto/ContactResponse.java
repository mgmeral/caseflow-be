package com.caseflow.customer.api.dto;

import java.time.Instant;

public record ContactResponse(
        Long id,
        Long customerId,
        String email,
        String name,
        Boolean isPrimary,
        Boolean isActive,
        Instant createdAt
) {}
