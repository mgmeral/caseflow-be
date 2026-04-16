package com.caseflow.ai.context.dto;

import java.util.List;

/**
 * Context assembled for a similar-cases AI request.
 */
public record SimilarCasesContext(
        String ticketNo,
        String subject,
        String problemSummary,
        List<String> tags,
        String customerCategory
) {}
