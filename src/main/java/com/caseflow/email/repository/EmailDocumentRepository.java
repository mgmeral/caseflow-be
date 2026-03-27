package com.caseflow.email.repository;

import com.caseflow.email.document.EmailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmailDocumentRepository extends MongoRepository<EmailDocument, String> {

    Optional<EmailDocument> findByMessageId(String messageId);

    List<EmailDocument> findByTicketId(Long ticketId);

    List<EmailDocument> findByThreadKey(String threadKey);
}
