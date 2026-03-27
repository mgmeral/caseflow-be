package com.caseflow.email.repository;

import com.caseflow.email.document.EmailDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EmailDocumentRepository extends MongoRepository<EmailDocument, String> {

    List<EmailDocument> findByTicketId(Long ticketId);

    List<EmailDocument> findByThreadKey(String threadKey);
}
