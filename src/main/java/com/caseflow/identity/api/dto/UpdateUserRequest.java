package com.caseflow.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRequest(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 255)
        String fullName,

        @NotNull
        Long roleId,

        @NotNull
        Boolean isActive,

        /**
         * If null, groups are left unchanged.
         * If an empty list, all group memberships are cleared.
         * If non-empty, group memberships are replaced with the given IDs.
         */
        List<Long> groupIds,

        /**
         * If null, the existing password is not changed.
         */
        @Size(min = 8, max = 128)
        String password
) {}
