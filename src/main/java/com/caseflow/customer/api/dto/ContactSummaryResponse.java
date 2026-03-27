package com.caseflow.customer.api.dto;

public record ContactSummaryResponse(
        Long id,
        Long customerId,
        String email,
        String name,
        Boolean isPrimary
) {}
