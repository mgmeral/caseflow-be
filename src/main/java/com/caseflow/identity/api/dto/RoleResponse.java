package com.caseflow.identity.api.dto;

import com.caseflow.identity.domain.TicketScope;

import java.time.Instant;
import java.util.Set;

public record RoleResponse(
        Long id,
        String code,
        String name,
        String description,
        Boolean isActive,
        TicketScope ticketScope,
        Set<String> permissionCodes,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {}
