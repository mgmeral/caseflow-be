package com.caseflow.knowledge.service;

import com.caseflow.ai.service.AiDocumentSyncService;
import com.caseflow.common.exception.KnowledgeDocumentNotFoundException;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.knowledge.api.dto.KnowledgeDocumentRequest;
import com.caseflow.knowledge.domain.KnowledgeDocument;
import com.caseflow.knowledge.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for knowledge documents. Every change enqueues an AI index sync in the same
 * transaction ({@link AiDocumentSyncService}), so the policy index follows the database.
 */
@Service
public class KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    private final KnowledgeDocumentRepository repository;
    private final CustomerRepository customerRepository;
    private final AiDocumentSyncService syncService;

    public KnowledgeDocumentService(KnowledgeDocumentRepository repository,
                                    CustomerRepository customerRepository,
                                    AiDocumentSyncService syncService) {
        this.repository = repository;
        this.customerRepository = customerRepository;
        this.syncService = syncService;
    }

    @Transactional(readOnly = true)
    public Page<KnowledgeDocument> list(Pageable pageable) {
        return repository.findAllByOrderByUpdatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument get(Long id) {
        return repository.findById(id).orElseThrow(() -> new KnowledgeDocumentNotFoundException(id));
    }

    @Transactional
    public KnowledgeDocument create(KnowledgeDocumentRequest request, Long userId) {
        KnowledgeDocument doc = new KnowledgeDocument();
        apply(doc, request);
        doc.setIsActive(request.active() == null || request.active());
        doc.setCreatedBy(userId);
        doc.setUpdatedBy(userId);
        KnowledgeDocument saved = repository.save(doc);
        syncService.requestSync(saved, "USER", userId);
        log.info("Knowledge document created — id: {}, customerId: {}", saved.getId(), saved.getCustomerId());
        return saved;
    }

    @Transactional
    public KnowledgeDocument update(Long id, KnowledgeDocumentRequest request, Long userId) {
        KnowledgeDocument doc = get(id);
        apply(doc, request);
        if (request.active() != null) doc.setIsActive(request.active());
        doc.setUpdatedBy(userId);
        KnowledgeDocument saved = repository.save(doc);
        syncService.requestSync(saved, "USER", userId);
        log.info("Knowledge document updated — id: {}", id);
        return saved;
    }

    @Transactional
    public KnowledgeDocument setActive(Long id, boolean active, Long userId) {
        KnowledgeDocument doc = get(id);
        doc.setIsActive(active);
        doc.setUpdatedBy(userId);
        KnowledgeDocument saved = repository.save(doc);
        syncService.requestSync(saved, "USER", userId);
        log.info("Knowledge document {} — id: {}", active ? "activated" : "deactivated", id);
        return saved;
    }

    @Transactional
    public void delete(Long id, Long userId) {
        KnowledgeDocument doc = get(id);
        // Enqueue before deleting: the job payload keeps the publicId needed to un-index it.
        syncService.requestSync(doc, "USER", userId);
        repository.delete(doc);
        log.info("Knowledge document deleted — id: {}", id);
    }

    private void apply(KnowledgeDocument doc, KnowledgeDocumentRequest request) {
        if (request.customerId() != null && !customerRepository.existsById(request.customerId())) {
            throw new IllegalArgumentException("Customer not found: " + request.customerId());
        }
        doc.setTitle(request.title().strip());
        doc.setBody(request.body());
        doc.setCategory(request.category() != null && !request.category().isBlank() ? request.category().strip() : null);
        doc.setCustomerId(request.customerId());
    }
}
