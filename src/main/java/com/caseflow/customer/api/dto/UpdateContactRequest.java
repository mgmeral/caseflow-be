package com.caseflow.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateContactRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        boolean isPrimary,

        boolean isActive
) {}
