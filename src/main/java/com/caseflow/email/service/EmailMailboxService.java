package com.caseflow.email.service;

import com.caseflow.common.exception.InvalidMailboxConfigException;
import com.caseflow.common.exception.MailboxNotFoundException;
import com.caseflow.email.api.dto.MailboxConnectionTestResponse;
import com.caseflow.email.api.dto.SmtpConnectionTestResponse;
import com.caseflow.email.domain.EmailMailbox;
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
        if (mailbox.getImapHost() == null || mailbox.getImapUsername() == null
                || mailbox.getImapPassword() == null) {
            return new MailboxConnectionTestResponse(false,
                    "Incomplete IMAP configuration — host, username, and password are required",
                    Instant.now());
        }

        return imapPoller.testImapConnection(mailbox);
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
