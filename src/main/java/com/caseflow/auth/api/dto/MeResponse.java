package com.caseflow.auth.api.dto;

public record MeResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String role
) {}
