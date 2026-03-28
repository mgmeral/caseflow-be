package com.caseflow.email.api.dto;

import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MailboxRequest(

        @NotBlank
        String name,

        @NotBlank
        @Email
        String address,

        @NotNull
        ProviderType providerType,

        @NotNull
        InboundMode inboundMode,

        @NotNull
        OutboundMode outboundMode,

        Boolean isActive,

        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        Boolean smtpUseSsl
) {}
