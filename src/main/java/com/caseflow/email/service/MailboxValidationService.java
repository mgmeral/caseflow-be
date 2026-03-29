package com.caseflow.email.service;

import com.caseflow.common.exception.InvalidMailboxConfigException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InboundMode;
import com.caseflow.email.domain.ProviderType;
import org.springframework.stereotype.Service;

/**
 * Validates mailbox configuration invariants before create, update, or activation.
 *
 * <p>Invalid configurations fail fast here rather than surfacing as scheduler errors at runtime.
 */
@Service
public class MailboxValidationService {

    private static final int MIN_POLL_INTERVAL_SECONDS = 30;
    private static final int MAX_POLL_INTERVAL_SECONDS = 86_400; // 24h

    /**
     * Validates the mailbox entity after all field updates have been applied.
     * Called for create, update (post-merge), and activate operations.
     *
     * @throws InvalidMailboxConfigException when any invariant is violated
     */
    public void validate(EmailMailbox mailbox) {
        boolean polling = Boolean.TRUE.equals(mailbox.getPollingEnabled());

        // Rule 1: WEBHOOK and SMTP_RELAY providers cannot use IMAP polling
        if (mailbox.getProviderType() == ProviderType.WEBHOOK
                || mailbox.getProviderType() == ProviderType.SMTP_RELAY) {
            if (polling) {
                throw new InvalidMailboxConfigException(
                        "providerType=" + mailbox.getProviderType()
                                + " does not support IMAP polling — set pollingEnabled=false");
            }
        }

        // Rule 2: polling requires IMAP provider and POLLING inbound mode
        if (polling) {
            if (mailbox.getProviderType() != ProviderType.IMAP) {
                throw new InvalidMailboxConfigException(
                        "pollingEnabled=true requires providerType=IMAP (got " + mailbox.getProviderType() + ")");
            }
            if (mailbox.getInboundMode() != InboundMode.POLLING) {
                throw new InvalidMailboxConfigException(
                        "pollingEnabled=true requires inboundMode=POLLING (got " + mailbox.getInboundMode() + ")");
            }
        }

        // Rule 3: IMAP polling mode requires complete IMAP credentials
        if (polling || mailbox.getInboundMode() == InboundMode.POLLING) {
            if (isBlank(mailbox.getImapHost())) {
                throw new InvalidMailboxConfigException("IMAP polling requires imapHost");
            }
            if (isBlank(mailbox.getImapUsername())) {
                throw new InvalidMailboxConfigException("IMAP polling requires imapUsername");
            }
            if (isBlank(mailbox.getImapPassword())) {
                throw new InvalidMailboxConfigException(
                        "IMAP polling requires imapPassword — provide the password on create or when changing credentials");
            }
            if (mailbox.getImapPort() == null) {
                throw new InvalidMailboxConfigException("IMAP polling requires imapPort");
            }
            if (isBlank(mailbox.getImapFolder())) {
                throw new InvalidMailboxConfigException("IMAP polling requires imapFolder (default: INBOX)");
            }
        }

        // Rule 4: pollIntervalSeconds range check
        if (polling) {
            int interval = mailbox.getPollIntervalSeconds() != null ? mailbox.getPollIntervalSeconds() : 60;
            if (interval < MIN_POLL_INTERVAL_SECONDS || interval > MAX_POLL_INTERVAL_SECONDS) {
                throw new InvalidMailboxConfigException(
                        "pollIntervalSeconds must be between " + MIN_POLL_INTERVAL_SECONDS
                                + " and " + MAX_POLL_INTERVAL_SECONDS + " (got " + interval + ")");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
