package com.caseflow.email.service;

import com.caseflow.common.exception.InvalidMailboxConfigException;
import com.caseflow.common.exception.MailboxNotFoundException;
import com.caseflow.email.api.dto.MailboxConnectionTestResponse;
import com.caseflow.email.api.dto.SmtpConnectionTestResponse;
import com.caseflow.email.domain.AuthType;
import com.caseflow.email.domain.CursorResetMode;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.domain.ProviderType;
import com.caseflow.email.repository.EmailMailboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EmailMailboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailMailboxService.class);

    private final EmailMailboxRepository mailboxRepository;
    private final MailboxValidationService validationService;
    private final ImapMailboxPoller imapPoller;
    private final SmtpEmailSender smtpSender;

    public EmailMailboxService(EmailMailboxRepository mailboxRepository,
                                MailboxValidationService validationService,
                                @Lazy ImapMailboxPoller imapPoller,
                                SmtpEmailSender smtpSender) {
        this.mailboxRepository = mailboxRepository;
        this.validationService = validationService;
        this.imapPoller = imapPoller;
        this.smtpSender = smtpSender;
    }

    @Transactional
    public EmailMailbox create(EmailMailbox mailbox) {
        validationService.validate(mailbox);
        EmailMailbox saved = mailboxRepository.save(mailbox);
        log.info("Mailbox created — id: {}, address: '{}'", saved.getId(), saved.getAddress());
        return saved;
    }

    @Transactional
    public EmailMailbox update(Long id, EmailMailbox updates) {
        EmailMailbox existing = findOrThrow(id);
        existing.setName(updates.getName());
        existing.setDisplayName(updates.getDisplayName());
        existing.setAddress(updates.getAddress());
        existing.setProviderType(updates.getProviderType());
        existing.setInboundMode(updates.getInboundMode());
        existing.setOutboundMode(updates.getOutboundMode());
        existing.setIsActive(updates.getIsActive());
        existing.setDefaultGroupId(updates.getDefaultGroupId());
        existing.setDefaultPriority(updates.getDefaultPriority());

        // SMTP — only update password if a new one is provided
        existing.setSmtpHost(updates.getSmtpHost());
        existing.setSmtpPort(updates.getSmtpPort());
        existing.setSmtpUsername(updates.getSmtpUsername());
        if (updates.getSmtpPassword() != null) {
            existing.setSmtpPassword(updates.getSmtpPassword());
        }
        existing.setSmtpUseSsl(updates.getSmtpUseSsl());
        existing.setSmtpStarttls(updates.getSmtpStarttls());

        // IMAP — only update password if a new one is provided
        existing.setImapHost(updates.getImapHost());
        existing.setImapPort(updates.getImapPort());
        existing.setImapUsername(updates.getImapUsername());
        if (updates.getImapPassword() != null) {
            existing.setImapPassword(updates.getImapPassword());
        }
        existing.setImapUseSsl(updates.getImapUseSsl());
        existing.setImapFolder(updates.getImapFolder());
        existing.setPollingEnabled(updates.getPollingEnabled());
        existing.setPollIntervalSeconds(updates.getPollIntervalSeconds());
        if (updates.getInitialSyncStrategy() != null) {
            existing.setInitialSyncStrategy(updates.getInitialSyncStrategy());
        }

        // Mail provider / auth type
        existing.setMailProvider(updates.getMailProvider());
        existing.setAuthType(updates.getAuthType());

        // OAuth2 — only update credentials when new values are provided
        existing.setOauthTenantId(updates.getOauthTenantId());
        existing.setOauthClientId(updates.getOauthClientId());
        if (updates.getOauthClientSecret() != null) {
            existing.setOauthClientSecret(updates.getOauthClientSecret());
        }

        // Validate the merged state
        validationService.validate(existing);

        EmailMailbox saved = mailboxRepository.save(existing);
        log.info("Mailbox updated — id: {}", id);
        return saved;
    }

    @Transactional
    public EmailMailbox activate(Long id) {
        EmailMailbox existing = findOrThrow(id);
        existing.setIsActive(Boolean.TRUE);
        validationService.validate(existing);
        EmailMailbox saved = mailboxRepository.save(existing);
        log.info("Mailbox activated — id: {}", id);
        return saved;
    }

    @Transactional
    public EmailMailbox deactivate(Long id) {
        EmailMailbox existing = findOrThrow(id);
        existing.setIsActive(Boolean.FALSE);
        EmailMailbox saved = mailboxRepository.save(existing);
        log.info("Mailbox deactivated — id: {}", id);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        findOrThrow(id);
        mailboxRepository.deleteById(id);
        log.info("Mailbox deleted — id: {}", id);
    }

    @Transactional(readOnly = true)
    public EmailMailbox getById(Long id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public List<EmailMailbox> findAll() {
        return mailboxRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EmailMailbox> findActive() {
        return mailboxRepository.findAllByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<EmailMailbox> findPollingEnabled() {
        return mailboxRepository.findAllByIsActiveTrueAndPollingEnabledTrue();
    }

    /**
     * Atomically claims a mailbox for polling by the given instance.
     * Returns true if the claim succeeded — false if another instance already holds the lease.
     */
    @Transactional
    public boolean tryClaimForPolling(Long mailboxId, String instanceId, Instant leaseExpiry) {
        return mailboxRepository.tryClaimMailbox(mailboxId, instanceId, leaseExpiry, Instant.now()) > 0;
    }

    /**
     * Tests IMAP connectivity for the given mailbox.
     * Always returns 200 OK; success/failure is conveyed in the response body.
     * Passwords are never exposed in the result message.
     */
    @Transactional(readOnly = true)
    public MailboxConnectionTestResponse testConnection(Long id) {
        EmailMailbox mailbox = findOrThrow(id);

        if (mailbox.getProviderType() != ProviderType.IMAP) {
            return new MailboxConnectionTestResponse(false,
                    "Connection test is only supported for IMAP mailboxes (providerType="
                            + mailbox.getProviderType() + ")",
                    Instant.now());
        }

        AuthType authType = mailbox.getAuthType() != null ? mailbox.getAuthType() : AuthType.PASSWORD;
        if (authType == AuthType.PASSWORD) {
            if (mailbox.getImapHost() == null || mailbox.getImapUsername() == null
                    || mailbox.getImapPassword() == null) {
                return new MailboxConnectionTestResponse(false,
                        "Incomplete IMAP configuration — host, username, and password are required for authType=PASSWORD",
                        Instant.now());
            }
        } else if (authType == AuthType.OAUTH2) {
            if (mailbox.getImapHost() == null || mailbox.getImapUsername() == null) {
                return new MailboxConnectionTestResponse(false,
                        "Incomplete IMAP configuration — host and username are required for authType=OAUTH2",
                        Instant.now());
            }
            if (mailbox.getOauthTenantId() == null || mailbox.getOauthClientId() == null
                    || mailbox.getOauthClientSecret() == null) {
                return new MailboxConnectionTestResponse(false,
                        "Incomplete OAuth2 configuration — oauthTenantId, oauthClientId, and oauthClientSecret are required for authType=OAUTH2",
                        Instant.now());
            }
        }

        return imapPoller.testImapConnection(mailbox);
    }

    /**
     * Triggers an immediate IMAP poll for the given mailbox, bypassing the
     * {@code pollingEnabled} guard and the scheduler interval check.
     *
     * <p>Intended for operator-initiated recovery. The poll runs synchronously;
     * all lifecycle state (lastPollAt, lastPollError, lease) is updated before returning.
     * Returns gracefully on connection failure — check {@code lastPollError} in the response.
     *
     * @throws IllegalStateException if the mailbox has no IMAP configuration
     */
    public void pollNow(Long mailboxId) {
        EmailMailbox mailbox = findOrThrow(mailboxId);
        if (mailbox.getImapHost() == null || mailbox.getImapUsername() == null) {
            throw new IllegalStateException(
                    "Mailbox " + mailboxId + " has no IMAP configuration — cannot poll");
        }
        log.info("POLL_NOW operator-triggered — mailboxId: {}", mailboxId);
        imapPoller.forcePoll(mailbox);
    }

    /**
     * Resets the IMAP cursor (lastSeenUid) for the given mailbox.
     *
     * <ul>
     *   <li>{@code CLEAR_FOR_REINIT} — sets lastSeenUid = null; next poll re-enters initial sync
     *       using the mailbox's existing {@code initialSyncStrategy}.</li>
     *   <li>{@code SET_TO_LATEST} — sets lastSeenUid = null and initialSyncStrategy =
     *       NEW_MESSAGES_ONLY; next poll will advance to the current inbox top without
     *       ingesting historical messages.</li>
     *   <li>{@code SET_EXPLICIT_UID} — sets lastSeenUid to the provided value; next poll
     *       processes messages with UID &gt; explicitUid.</li>
     * </ul>
     *
     * @return the updated mailbox entity
     */
    @Transactional
    public EmailMailbox resetCursor(Long mailboxId, CursorResetMode mode, Long explicitUid) {
        EmailMailbox mailbox = findOrThrow(mailboxId);
        switch (mode) {
            case CLEAR_FOR_REINIT -> {
                mailbox.setLastSeenUid(null);
                log.info("CURSOR_RESET CLEAR_FOR_REINIT — mailboxId: {}", mailboxId);
            }
            case SET_TO_LATEST -> {
                mailbox.setLastSeenUid(null);
                mailbox.setInitialSyncStrategy(InitialSyncStrategy.NEW_MESSAGES_ONLY);
                log.info("CURSOR_RESET SET_TO_LATEST — mailboxId: {} (next poll will advance to latest without ingesting history)",
                        mailboxId);
            }
            case SET_EXPLICIT_UID -> {
                if (explicitUid == null) {
                    throw new IllegalArgumentException(
                            "explicitUid is required for CursorResetMode.SET_EXPLICIT_UID");
                }
                mailbox.setLastSeenUid(explicitUid);
                log.info("CURSOR_RESET SET_EXPLICIT_UID — mailboxId: {}, uid: {}", mailboxId, explicitUid);
            }
        }
        return mailboxRepository.save(mailbox);
    }

    /**
     * Tests SMTP connectivity for the given mailbox.
     * Validates config and attempts a TCP connect to the SMTP host:port.
     * Returns 200 OK always; success/failure is in the response body.
     */
    @Transactional(readOnly = true)
    public SmtpConnectionTestResponse testSmtpConnection(Long id) {
        EmailMailbox mailbox = findOrThrow(id);
        log.info("SMTP_TEST_CONNECTION requested — mailboxId: {}", id);
        return smtpSender.testSmtpConnection(mailbox);
    }

    private EmailMailbox findOrThrow(Long id) {
        return mailboxRepository.findById(id)
                .orElseThrow(() -> new MailboxNotFoundException(id));
    }
}
