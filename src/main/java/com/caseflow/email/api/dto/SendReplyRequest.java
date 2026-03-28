package com.caseflow.email.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendReplyRequest(

        @NotNull
        Long mailboxId,

        @NotBlank
        @Email
        String toAddress,

        @NotBlank
        String subject,

        String textBody,

        String htmlBody,

        /** messageId of the customer email being replied to — used for In-Reply-To threading. */
        String inReplyToMessageId
) {}
