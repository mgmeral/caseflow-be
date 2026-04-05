package com.caseflow.ticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a tag.
 *
 * <p>The {@code code} is normalized to UPPER_SNAKE_CASE by the service.
 * Once created, the code is immutable to preserve historical reporting references.
 */
public record TagRequest(

        /**
         * Unique tag code — normalized to uppercase.
         * Required only on create; ignored on update (code is immutable).
         */
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]+$",
                 message = "Tag code must contain only letters, digits, and underscores")
        @Size(max = 100)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description,

        /** Optional UI color hint (CSS value). */
        @Size(max = 20)
        String color,

        Boolean isActive
) {}
