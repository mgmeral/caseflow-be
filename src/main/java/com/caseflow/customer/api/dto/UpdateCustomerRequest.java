package com.caseflow.customer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 100)
        String code
) {}
