package com.caseflow.sla.api.dto;

import com.caseflow.sla.domain.SlaScope;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlaPolicyRequest(

        @NotBlank @Size(max = 255)
        String name,

        @NotNull
        SlaScope scope,

        /** Required when scope = PRIORITY. Must match a TicketPriority name. */
        String priority,

        Long groupId,

        @Min(1)
        int firstResponseTargetMinutes,

        @Min(1)
        int resolutionTargetMinutes,

        @Min(1)
        int warningBeforeBreachMinutes,

        Boolean isActive
) {}
