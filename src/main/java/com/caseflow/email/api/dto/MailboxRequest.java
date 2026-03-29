package com.caseflow.email.api.dto;

import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MailboxRequest(

        @NotBlank
        String name,

        String displayName,

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

        Long defaultGroupId,
        String defaultPriority,

        // SMTP outbound
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        Boolean smtpUseSsl,

        // IMAP inbound polling
        String imapHost,
        Integer imapPort,
        String imapUsername,
        String imapPassword,
        Boolean imapUseSsl,
        String imapFolder,
        Boolean pollingEnabled,
        Integer pollIntervalSeconds,

        /**
         * Controls what is ingested on the first poll when lastSeenUid is null.
         * Defaults to START_FROM_LATEST (production-safe).  Use BACKFILL_ALL to intentionally
         * import historical inbox messages on new mailbox onboarding.
         */
        InitialSyncStrategy initialSyncStrategy
) {}
