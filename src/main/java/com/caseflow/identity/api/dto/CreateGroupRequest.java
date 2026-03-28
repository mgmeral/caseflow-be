package com.caseflow.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        Long groupTypeId,

        @Size(max = 1000)
        String description,

        List<Long> userIds
) {}
