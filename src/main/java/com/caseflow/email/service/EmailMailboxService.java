package com.caseflow.email.service;

import com.caseflow.common.exception.MailboxNotFoundException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.repository.EmailMailboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmailMailboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailMailboxService.class);

    private final EmailMailboxRepository mailboxRepository;

    public EmailMailboxService(EmailMailboxRepository mailboxRepository) {
        this.mailboxRepository = mailboxRepository;
    }

    @Transactional
    public EmailMailbox create(EmailMailbox mailbox) {
        EmailMailbox saved = mailboxRepository.save(mailbox);
        log.info("Mailbox created — id: {}, address: '{}'", saved.getId(), saved.getAddress());
        return saved;
    }

    @Transactional
    public EmailMailbox update(Long id, EmailMailbox updates) {
        EmailMailbox existing = findOrThrow(id);
        existing.setName(updates.getName());
        existing.setAddress(updates.getAddress());
        existing.setProviderType(updates.getProviderType());
        existing.setInboundMode(updates.getInboundMode());
        existing.setOutboundMode(updates.getOutboundMode());
        existing.setIsActive(updates.getIsActive());
        existing.setSmtpHost(updates.getSmtpHost());
        existing.setSmtpPort(updates.getSmtpPort());
        existing.setSmtpUsername(updates.getSmtpUsername());
        if (updates.getSmtpPassword() != null) {
            existing.setSmtpPassword(updates.getSmtpPassword());
        }
        existing.setSmtpUseSsl(updates.getSmtpUseSsl());
        EmailMailbox saved = mailboxRepository.save(existing);
        log.info("Mailbox updated — id: {}", id);
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

    private EmailMailbox findOrThrow(Long id) {
        return mailboxRepository.findById(id)
                .orElseThrow(() -> new MailboxNotFoundException(id));
    }
}
