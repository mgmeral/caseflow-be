package com.caseflow.email.api.dto;

import com.caseflow.email.domain.AuthType;
import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.domain.MailProvider;
import com.caseflow.email.domain.OutboundMode;
import com.caseflow.email.domain.ProviderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Mailbox API response — passwords (SMTP, IMAP, OAuth2 client secret) are never exposed.
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

        // Mail provider / auth
        @Schema(description = "Mail vendor. Controls auth defaults.")
        MailProvider mailProvider,

        @Schema(description = "Authentication mechanism used for IMAP.")
        AuthType authType,

        @Schema(description = "Azure AD tenant ID (non-secret, safe to expose).")
        String oauthTenantId,

        @Schema(description = "Azure AD application (client) ID (non-secret, safe to expose).")
        String oauthClientId,

        @Schema(description = "True when all three OAuth2 credentials are stored (tenantId, clientId, clientSecret).")
        Boolean oauthConfigured,

        Instant lastSuccessfulInboundAt,
        Instant lastSuccessfulOutboundAt,
        Instant createdAt,
        Instant updatedAt
) {}
