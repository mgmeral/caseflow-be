package com.caseflow.email.service;

import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves email thread keys and links emails to existing tickets via header chain.
 *
 * <p>Thread resolution precedence:
 * <ol>
 *   <li>In-Reply-To header → look up parent document by messageId</li>
 *   <li>References header chain → first match wins</li>
 *   <li>Subject token matching (RE:/FWD: stripped subject) against open threads</li>
 *   <li>New thread — use own messageId as threadKey</li>
 * </ol>
 */
@Service
public class EmailThreadingService {

    private final EmailDocumentRepository emailDocumentRepository;

    public EmailThreadingService(EmailDocumentRepository emailDocumentRepository) {
        this.emailDocumentRepository = emailDocumentRepository;
    }

    /**
     * Derives the threadKey for an incoming email. If a parent document is found,
     * the threadKey is inherited; otherwise a new threadKey is generated.
     */
    public String resolveThreadKey(String inReplyTo, List<String> references, String messageId) {
        // 1. In-Reply-To
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            Optional<EmailDocument> parent = emailDocumentRepository.findByMessageId(inReplyTo);
            if (parent.isPresent()) {
                return parent.get().getThreadKey();
            }
        }

        // 2. References chain (first existing document wins)
        if (references != null) {
            for (String ref : references) {
                Optional<EmailDocument> refDoc = emailDocumentRepository.findByMessageId(ref);
                if (refDoc.isPresent()) {
                    return refDoc.get().getThreadKey();
                }
            }
        }

        // 3. New thread — anchor on own messageId, or generate a UUID
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Resolves the ticketId for an incoming email by walking the header chain.
     * Returns empty if no existing ticket is found.
     */
    public Optional<Long> resolveTicketId(String inReplyTo, List<String> references) {
        // 1. In-Reply-To
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            Optional<Long> ticketId = emailDocumentRepository.findByMessageId(inReplyTo)
                    .map(EmailDocument::getTicketId)
                    .filter(id -> id != null);
            if (ticketId.isPresent()) {
                return ticketId;
            }
        }

        // 2. References chain
        if (references != null) {
            for (String ref : references) {
                Optional<Long> ticketId = emailDocumentRepository.findByMessageId(ref)
                        .map(EmailDocument::getTicketId)
                        .filter(id -> id != null);
                if (ticketId.isPresent()) {
                    return ticketId;
                }
            }
        }

        return Optional.empty();
    }
}
