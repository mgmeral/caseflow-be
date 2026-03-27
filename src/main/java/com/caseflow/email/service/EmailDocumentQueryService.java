package com.caseflow.email.service;

import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EmailDocumentQueryService {

    private final EmailDocumentRepository emailDocumentRepository;

    public EmailDocumentQueryService(EmailDocumentRepository emailDocumentRepository) {
        this.emailDocumentRepository = emailDocumentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<EmailDocument> findById(String id) {
        return emailDocumentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<EmailDocument> findByTicketId(Long ticketId) {
        return emailDocumentRepository.findByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public List<EmailDocument> findByThreadKey(String threadKey) {
        return emailDocumentRepository.findByThreadKey(threadKey);
    }
}
