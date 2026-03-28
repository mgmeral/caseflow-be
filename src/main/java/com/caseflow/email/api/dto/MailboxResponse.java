package com.caseflow.email.api.dto;

import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;

import java.time.Instant;

public record MailboxResponse(
        Long id,
        String name,
        String address,
        ProviderType providerType,
        InboundMode inboundMode,
        OutboundMode outboundMode,
        Boolean isActive,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        Boolean smtpUseSsl,
        Instant createdAt,
        Instant updatedAt
) {}
