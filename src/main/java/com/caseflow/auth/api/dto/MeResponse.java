package com.caseflow.auth.api.dto;

import java.util.List;

public record MeResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Long roleId,
        String roleCode,
        String roleName,
        List<String> permissionCodes,
        String ticketScope,
        List<Long> groupIds
) {}
