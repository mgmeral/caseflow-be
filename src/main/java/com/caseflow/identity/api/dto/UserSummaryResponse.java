package com.caseflow.identity.api.dto;

public record UserSummaryResponse(
        Long id,
        String username,
        String fullName,
        Long roleId,
        String roleCode,
        Boolean isActive,
        /** Open (non-terminal), assigned ticket count — used by agent-picker UIs to show current workload. */
        Long openTicketCount
) {}
