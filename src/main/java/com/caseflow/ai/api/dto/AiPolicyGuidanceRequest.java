package com.caseflow.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/tickets/{id}/ai-policy-guidance}.
 */
public record AiPolicyGuidanceRequest(
        @NotBlank @Size(max = 500)
        String question
) {}
