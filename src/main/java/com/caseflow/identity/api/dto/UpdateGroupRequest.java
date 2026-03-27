package com.caseflow.identity.api.dto;

import com.caseflow.identity.domain.GroupType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        GroupType type
) {}
