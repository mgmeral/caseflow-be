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

        Boolean isActive
) {}
