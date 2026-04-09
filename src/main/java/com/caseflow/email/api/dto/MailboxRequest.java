package com.caseflow.email.api.dto;

import com.caseflow.email.domain.AuthType;
import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.domain.MailProvider;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;
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

        /**
         * When true, use STARTTLS (explicit TLS upgrade) for outbound SMTP — correct for port 587.
         * Mutually exclusive with {@code smtpUseSsl}. Set smtpUseSsl=true for port 465 (implicit SSL).
         */
        Boolean smtpStarttls,

        // IMAP inbound polling
        String imapHost,
        Integer imapPort,
        String imapUsername,

        @Schema(description = "IMAP password — required for authType=PASSWORD. Write-only; never returned in responses.")
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
        InitialSyncStrategy initialSyncStrategy,

        // Mail provider / auth type
        @Schema(description = "Mail provider / vendor. Controls auth defaults. Defaults to OTHER.")
        MailProvider mailProvider,

        @Schema(description = "Authentication mechanism for IMAP. OUTLOOK mailboxes must use OAUTH2.")
        AuthType authType,

        // OAuth2 credentials — required when authType=OAUTH2
        @Schema(description = "Azure AD tenant ID — required for authType=OAUTH2.")
        String oauthTenantId,

        @Schema(description = "Azure AD application (client) ID — required for authType=OAUTH2.")
        String oauthClientId,

        @Schema(description = "Azure AD client secret — required for authType=OAUTH2. Write-only; never returned in responses.")
        String oauthClientSecret
) {}
