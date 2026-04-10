package com.caseflow.email.api.dto;

import com.caseflow.email.domain.MailTemplate;

import java.time.Instant;

public record MailTemplateResponse(
        Long id,
        String code,
        String name,
        /** Semantic usage type, e.g. CUSTOMER_REPLY, ACKNOWLEDGEMENT, FOLLOW_UP. */
        String usageType,
        /** Human-readable description of when/why to use this template. */
        String description,
        String subjectTemplate,
        String htmlTemplate,
        String plainTextTemplate,
        Boolean isActive,
        Boolean isBuiltIn,
        Instant createdAt,
        Instant updatedAt
) {
    public static MailTemplateResponse from(MailTemplate t) {
        return new MailTemplateResponse(
                t.getId(), t.getCode(), t.getName(),
                t.getUsageType(), t.getDescription(),
                t.getSubjectTemplate(), t.getHtmlTemplate(), t.getPlainTextTemplate(),
                t.getIsActive(), t.getIsBuiltIn(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
