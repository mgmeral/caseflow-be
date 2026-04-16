package com.caseflow.ai.context.dto;

import java.util.List;

/**
 * Context assembled for a policy-guidance AI request.
 */
public record PolicyGuidanceContext(
        String ticketNo,
        String userQuestion,
        String subject,
        String status,
        List<String> tags,
        String customerName,
        String locale,
        List<String> scopeHints
) {}
