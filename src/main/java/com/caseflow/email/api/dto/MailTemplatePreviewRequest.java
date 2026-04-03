package com.caseflow.email.api.dto;

public record MailTemplatePreviewRequest(
        String replyBody,
        String ticketRef,
        String mailboxName,
        String agentName,
        String signatureBlock
) {}
