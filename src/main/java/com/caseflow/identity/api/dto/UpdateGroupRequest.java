package com.caseflow.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateGroupRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        Long groupTypeId,

        @Size(max = 1000)
        String description,

        /**
         * If null, existing members are unchanged.
         * If an empty list, all members are removed.
         * If non-empty, the member list is replaced with these user IDs.
         */
        List<Long> userIds
) {}
