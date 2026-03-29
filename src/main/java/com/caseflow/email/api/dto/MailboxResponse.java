package com.caseflow.email.api.dto;

import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;

import java.time.Instant;

public record MailboxResponse(
        Long id,
        String name,
        String displayName,
        String address,
        ProviderType providerType,
        InboundMode inboundMode,
        OutboundMode outboundMode,
        Boolean isActive,
        Long defaultGroupId,
        String defaultPriority,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        Boolean smtpUseSsl,
        Instant lastSuccessfulInboundAt,
        Instant lastSuccessfulOutboundAt,
        Instant createdAt,
        Instant updatedAt
) {}
