package com.caseflow.identity.api.dto;

import com.caseflow.identity.domain.TicketScope;

public record RoleSummaryResponse(
        Long id,
        String code,
        String name,
        Boolean isActive,
        TicketScope ticketScope,
        int permissionCount
) {}
