package com.caseflow.email.api.dto;

import com.caseflow.email.domain.MailTemplate;

import java.time.Instant;

public record MailTemplateResponse(
        Long id,
        String code,
        String name,
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
                t.getSubjectTemplate(), t.getHtmlTemplate(), t.getPlainTextTemplate(),
                t.getIsActive(), t.getIsBuiltIn(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
