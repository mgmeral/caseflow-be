package com.caseflow.email.service;

import com.caseflow.common.exception.InvalidMailboxConfigException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailboxValidationServiceTest {

    private MailboxValidationService validator;

    @BeforeEach
    void setup() {
        validator = new MailboxValidationService();
    }

    // ── Valid configurations ──────────────────────────────────────────────────

    @Test
    void validate_passes_forImapMailboxWithFullConfig() {
        EmailMailbox mailbox = imapPollingMailbox();

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_forWebhookMailboxWithPollingDisabled() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.WEBHOOK);
        mailbox.setInboundMode(InboundMode.WEBHOOK);
        mailbox.setPollingEnabled(false);

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_forSmtpRelayWithPollingDisabled() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setInboundMode(InboundMode.WEBHOOK);
        mailbox.setPollingEnabled(false);

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_whenPollingEnabledIsNull() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setPollingEnabled(null);

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    // ── Rule 1 & 2: inconsistent providerType / inboundMode / pollingEnabled ──

    @Test
    void validate_rejects_webhookProviderWithPollingEnabled() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.WEBHOOK);
        mailbox.setInboundMode(InboundMode.WEBHOOK);
        mailbox.setPollingEnabled(true);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("WEBHOOK")
                .hasMessageContaining("pollingEnabled");
    }

    @Test
    void validate_rejects_smtpRelayWithPollingEnabled() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setPollingEnabled(true);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("SMTP_RELAY");
    }

    @Test
    void validate_rejects_pollingEnabledWithoutImapProviderType() {
        // IMAP provider with wrong inboundMode (WEBHOOK instead of POLLING)
        // — Rule 2 fires: polling requires inboundMode=POLLING
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setInboundMode(InboundMode.WEBHOOK);  // IMAP provider but wrong inbound mode
        mailbox.setPollingEnabled(true);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("inboundMode=POLLING");
    }

    @Test
    void validate_rejects_pollingEnabledWithoutPollingInboundMode() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setInboundMode(InboundMode.WEBHOOK);  // wrong mode

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("inboundMode=POLLING");
    }

    // ── Rule 3: missing required IMAP config ──────────────────────────────────

    @Test
    void validate_rejects_missingImapHost() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setImapHost(null);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("imapHost");
    }

    @Test
    void validate_rejects_missingImapUsername() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setImapUsername(null);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("imapUsername");
    }

    @Test
    void validate_rejects_missingImapPassword() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setImapPassword(null);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("imapPassword");
    }

    @Test
    void validate_rejects_missingImapPort() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setImapPort(null);

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("imapPort");
    }

    @Test
    void validate_rejects_blankImapFolder() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setImapFolder("   ");

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("imapFolder");
    }

    // ── Rule 4: pollIntervalSeconds range ─────────────────────────────────────

    @Test
    void validate_rejects_pollIntervalBelowMinimum() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setPollIntervalSeconds(10);  // below 30s minimum

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("pollIntervalSeconds");
    }

    @Test
    void validate_rejects_pollIntervalAboveMaximum() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setPollIntervalSeconds(100_000);  // above 86400s maximum

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("pollIntervalSeconds");
    }

    @Test
    void validate_passes_pollIntervalAtBoundary() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setPollIntervalSeconds(30);

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_pollIntervalAtMaxBoundary() {
        EmailMailbox mailbox = imapPollingMailbox();
        mailbox.setPollIntervalSeconds(86_400);

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    // ── Rule 5: SMTP transport mode contradiction ─────────────────────────────

    @Test
    void validate_rejects_smtpUseSslAndStarttlsBothTrue() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setPollingEnabled(false);
        mailbox.setSmtpUseSsl(true);
        mailbox.setSmtpStarttls(true);  // contradictory — cannot have both

        assertThatThrownBy(() -> validator.validate(mailbox))
                .isInstanceOf(InvalidMailboxConfigException.class)
                .hasMessageContaining("mutually exclusive")
                .hasMessageContaining("smtpUseSsl")
                .hasMessageContaining("smtpStarttls");
    }

    @Test
    void validate_passes_smtpImplicitSsl_port465() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setPollingEnabled(false);
        mailbox.setSmtpUseSsl(true);
        mailbox.setSmtpStarttls(false);  // correct for port 465

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_smtpStarttls_port587() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setPollingEnabled(false);
        mailbox.setSmtpUseSsl(false);
        mailbox.setSmtpStarttls(true);  // correct for port 587

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_smtpNoTls_plainSmtp() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.SMTP_RELAY);
        mailbox.setPollingEnabled(false);
        mailbox.setSmtpUseSsl(false);
        mailbox.setSmtpStarttls(false);  // plain SMTP — allowed (not recommended but valid)

        assertThatCode(() -> validator.validate(mailbox)).doesNotThrowAnyException();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private EmailMailbox imapPollingMailbox() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setProviderType(ProviderType.IMAP);
        mailbox.setInboundMode(InboundMode.POLLING);
        mailbox.setPollingEnabled(true);
        mailbox.setImapHost("imap.example.com");
        mailbox.setImapPort(993);
        mailbox.setImapUsername("inbox@example.com");
        mailbox.setImapPassword("secret");
        mailbox.setImapUseSsl(true);
        mailbox.setImapFolder("INBOX");
        mailbox.setPollIntervalSeconds(60);
        return mailbox;
    }
}
