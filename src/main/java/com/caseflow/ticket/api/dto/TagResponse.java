package com.caseflow.ticket.api.dto;

import java.time.Instant;

public record TagResponse(
        Long id,
        String code,
        String name,
        String description,
        String color,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
