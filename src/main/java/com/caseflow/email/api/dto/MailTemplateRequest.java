package com.caseflow.email.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MailTemplateRequest(

        @NotBlank
        @Size(max = 100)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String subjectTemplate,

        @NotBlank
        String htmlTemplate,

        @NotBlank
        String plainTextTemplate,

        Boolean isActive,

        /**
         * Semantic usage type for FE guidance — e.g. CUSTOMER_REPLY, ACKNOWLEDGEMENT,
         * FOLLOW_UP, RESOLUTION, NEED_MORE_INFO. Optional; stored as-is (no enum validation).
         */
        @Size(max = 50)
        String usageType,

        /** Optional description of when/why to use this template. */
        @Size(max = 1000)
        String description
) {}
