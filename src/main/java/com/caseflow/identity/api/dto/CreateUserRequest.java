package com.caseflow.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(

        @NotBlank
        @Size(max = 100)
        String username,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 255)
        String fullName,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotNull
        Long roleId,

        List<Long> groupIds,

        Boolean isActive
) {}
