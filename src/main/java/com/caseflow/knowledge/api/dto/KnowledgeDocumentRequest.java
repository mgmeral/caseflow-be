package com.caseflow.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update body for a knowledge document. {@code customerId} null = applies to every
 * customer; {@code active} null = true on create, unchanged on update.
 */
public record KnowledgeDocumentRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100_000) String body,
        @Size(max = 100) String category,
        Long customerId,
        Boolean active
) {}
