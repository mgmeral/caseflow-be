package com.caseflow.email.api.dto;

import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;

import java.time.Instant;

/**
 * Mailbox API response — passwords (SMTP and IMAP) are never exposed.
 */
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

        // SMTP (no password)
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        Boolean smtpUseSsl,
        /** Whether STARTTLS is used for port-587 outbound. Mutually exclusive with smtpUseSsl. */
        Boolean smtpStarttls,

        // IMAP polling (no password)
        String imapHost,
        Integer imapPort,
        String imapUsername,
        Boolean imapUseSsl,
        String imapFolder,
        Boolean pollingEnabled,
        Integer pollIntervalSeconds,
        InitialSyncStrategy initialSyncStrategy,
        Long lastSeenUid,
        Instant lastPollAt,
        String lastPollError,

        Instant lastSuccessfulInboundAt,
        Instant lastSuccessfulOutboundAt,
        Instant createdAt,
        Instant updatedAt
) {}
