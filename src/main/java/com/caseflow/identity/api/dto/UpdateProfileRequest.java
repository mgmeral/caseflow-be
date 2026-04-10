package com.caseflow.identity.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 10)  String locale
) {}
