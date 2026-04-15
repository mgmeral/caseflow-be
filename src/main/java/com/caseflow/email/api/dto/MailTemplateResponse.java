package com.caseflow.email.api.dto;

import com.caseflow.email.domain.MailTemplate;

import java.time.Instant;

/**
 * Full template detail response with productivity metadata.
 *
 * <h2>Macro-picker contract</h2>
 * FE can use {@code usageType}, {@code description}, {@code supportedPlaceholders},
 * {@code customerVisible}, and {@code defaultStatusAfterSend} to build a smart reply composer.
 */
public record MailTemplateResponse(
        Long id,
        String code,
        String name,
        /** Semantic usage type: CUSTOMER_REPLY, ACKNOWLEDGEMENT, NEED_MORE_INFO, FOLLOW_UP, RESOLUTION, INTERNAL_UPDATE. */
        String usageType,
        /** Human-readable description of when/why to use this template. */
        String description,
        /** Comma-separated placeholder tokens used in this template, e.g. "{replyBody},{ticketRef}". */
        String supportedPlaceholders,
        /** True when the email content is intended for the customer to see. False for internal-only. */
        Boolean customerVisible,
        /**
         * Optional default ticket status to apply after sending with this template.
         * E.g. "WAITING_CUSTOMER" or "RESOLVED". Null = no default transition.
         */
        String defaultStatusAfterSend,
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
                t.getSupportedPlaceholders(),
                t.getCustomerVisible(),
                t.getDefaultStatusAfterSend(),
                t.getSubjectTemplate(), t.getHtmlTemplate(), t.getPlainTextTemplate(),
                t.getIsActive(), t.getIsBuiltIn(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
