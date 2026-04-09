package com.caseflow.note.api.dto;

/**
 * Compact user reference embedded in note responses.
 * Allows FE to render author and mentioned users without extra user-lookup calls.
 */
public record UserSummary(
        Long id,
        String username,
        String displayName
) {}
