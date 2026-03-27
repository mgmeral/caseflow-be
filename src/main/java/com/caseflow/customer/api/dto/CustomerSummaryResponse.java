package com.caseflow.customer.api.dto;

public record CustomerSummaryResponse(
        Long id,
        String name,
        String code
) {}
