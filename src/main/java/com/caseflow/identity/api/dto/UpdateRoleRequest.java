package com.caseflow.identity.api.dto;

import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.domain.TicketScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRoleRequest(

        @NotBlank
        @Size(max = 100)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description,

        @NotNull
        TicketScope ticketScope,

        @NotNull
        Set<Permission> permissions
) {}
