package com.caseflow.email.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MailTemplateRequest(

        @NotBlank
        @Size(max = 100)
        String code,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String subjectTemplate,

        @NotBlank
        String htmlTemplate,

        @NotBlank
        String plainTextTemplate,

        Boolean isActive,

        /**
         * Semantic usage type for FE guidance — e.g. CUSTOMER_REPLY, ACKNOWLEDGEMENT,
         * FOLLOW_UP, RESOLUTION, NEED_MORE_INFO, INTERNAL_UPDATE. Stored as-is.
         */
        @Size(max = 50)
        String usageType,

        /** Optional description of when/why to use this template. */
        @Size(max = 1000)
        String description,

        /**
         * Comma-separated placeholder tokens used in this template body.
         * Example: "{replyBody},{ticketRef},{agentName}". Informational — guides FE picker.
         */
        String supportedPlaceholders,

        /**
         * Whether this template produces customer-visible email content.
         * false = INTERNAL_UPDATE or agent-only context. Defaults to true.
         */
        Boolean customerVisible,

        /**
         * Optional default ticket status to set after applying this template in a reply.
         * Must be a valid TicketStatus name (e.g. WAITING_CUSTOMER, RESOLVED).
         * Null = no default transition.
         */
        @Size(max = 50)
        String defaultStatusAfterSend
) {}
