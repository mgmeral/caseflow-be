package com.caseflow.email.api.dto;

import jakarta.validation.constraints.NotBlank;

public record QuarantineRequest(
        @NotBlank
        String reason
) {}
