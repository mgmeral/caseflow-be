package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailIngressEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmailIngressEventQueryService {

    private final EmailIngressEventRepository eventRepository;

    public EmailIngressEventQueryService(EmailIngressEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public EmailIngressEvent getById(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IngressEventNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<EmailIngressEvent> findByStatus(IngressEventStatus status) {
        return eventRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<EmailIngressEvent> findByTicketId(Long ticketId) {
        return eventRepository.findByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public List<EmailIngressEvent> findByMailboxId(Long mailboxId) {
        return eventRepository.findByMailboxId(mailboxId);
    }

    @Transactional(readOnly = true)
    public List<EmailIngressEvent> findAll() {
        return eventRepository.findAll();
    }
}
