package com.caseflow.ai.service;

import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.knowledge.domain.KnowledgeDocument;
import com.caseflow.knowledge.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Keeps caseflow-ai-service's policy index in step with knowledge-base documents over the
 * durable {@code integration_jobs} queue.
 *
 * <p>Callers enqueue inside the same transaction that changes the document, so a committed
 * change always has its sync job (and a rolled-back one never does). The job payload names the
 * document; {@link AiDocumentSyncProcessor} reads its current state when it runs and ingests it
 * (active) or removes it (inactive or deleted) — so jobs may run in any order.
 */
@Service
public class AiDocumentSyncService {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentSyncService.class);

    /** How knowledge documents are typed in the AI index; policy retrieval searches this type. */
    public static final String SOURCE_TYPE = "POLICY";

    private final IntegrationJobService jobService;
    private final KnowledgeDocumentRepository documentRepository;
    private final AiAvailabilityService availabilityService;
    private final ObjectMapper objectMapper;

    public AiDocumentSyncService(IntegrationJobService jobService,
                                 KnowledgeDocumentRepository documentRepository,
                                 AiAvailabilityService availabilityService,
                                 ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.documentRepository = documentRepository;
        this.availabilityService = availabilityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueues a sync for the document. Call after saving it, and before deleting it — the
     * payload keeps the publicId the processor needs to remove a deleted document.
     *
     * @return true if a job was enqueued
     */
    @Transactional
    public boolean requestSync(KnowledgeDocument document, String triggeredByType, Long userId) {
        if (!availabilityService.isAvailable()) {
            return false;
        }
        jobService.enqueue(IntegrationType.AI_DOCUMENT_SYNC, null, null, document.getCustomerId(), null,
                payload(document), "ai-document-sync:" + document.getId() + ":" + UUID.randomUUID(),
                triggeredByType, userId);
        log.debug("AI document sync enqueued [documentId={}, trigger={}]", document.getId(), triggeredByType);
        return true;
    }

    /** Enqueues a sync for every active document — part of the admin re-index. */
    @Transactional
    public int reindexAll() {
        int enqueued = 0;
        for (KnowledgeDocument document : documentRepository.findByIsActiveTrue()) {
            if (requestSync(document, "SYSTEM", null)) enqueued++;
        }
        log.info("AI document reindex requested — {} jobs enqueued", enqueued);
        return enqueued;
    }

    private String payload(KnowledgeDocument document) {
        try {
            return objectMapper.writeValueAsString(new SyncPayload(document.getId(), document.getPublicId()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize AI document sync payload", e);
        }
    }

    /** {@code integration_jobs.payload_json} of an {@code AI_DOCUMENT_SYNC} job. */
    public record SyncPayload(Long documentId, UUID publicId) {
        static SyncPayload from(ObjectMapper mapper, String json) throws JsonProcessingException {
            return mapper.readValue(json, SyncPayload.class);
        }
    }
}
