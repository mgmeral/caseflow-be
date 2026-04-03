package com.caseflow.email.api.dto;

public record MailTemplatePreviewResponse(
        String subject,
        String html,
        String text
) {}
