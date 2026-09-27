package com.caseflow.ai.service;

import com.caseflow.ai.client.AiServiceUnavailableException;
import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.request.AiDocumentIngestRequest;
import com.caseflow.ai.client.dto.response.AiIngestResponse;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobProcessor;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.knowledge.domain.KnowledgeDocument;
import com.caseflow.knowledge.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Processes {@link IntegrationType#AI_DOCUMENT_SYNC} jobs by reconciling the AI policy index with
 * the knowledge document as it is <em>now</em>: active documents are (re)ingested as
 * {@code POLICY}, inactive or deleted ones are removed. Failure semantics match
 * {@link AiTicketSyncProcessor}.
 */
@Component
public class AiDocumentSyncProcessor implements IntegrationJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentSyncProcessor.class);

    private final CaseflowAiClient aiClient;
    private final KnowledgeDocumentRepository documentRepository;
    private final IntegrationJobService jobService;
    private final ObjectMapper objectMapper;

    public AiDocumentSyncProcessor(CaseflowAiClient aiClient,
                                   KnowledgeDocumentRepository documentRepository,
                                   IntegrationJobService jobService,
                                   ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.documentRepository = documentRepository;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Override
    public IntegrationType supportedType() {
        return IntegrationType.AI_DOCUMENT_SYNC;
    }

    @Override
    public void process(IntegrationJob job) throws IntegrationJobExecutionException {
        AiDocumentSyncService.SyncPayload payload;
        try {
            payload = AiDocumentSyncService.SyncPayload.from(objectMapper, job.getPayloadJson());
        } catch (Exception e) {
            throw new IntegrationJobExecutionException("Unreadable AI document sync payload: " + e.getMessage(), true);
        }
        String correlationId = "ai-document-sync-" + job.getId();
        String sourceId = payload.publicId().toString();
        Optional<KnowledgeDocument> found = documentRepository.findById(payload.documentId());
        try {
            if (found.isPresent() && Boolean.TRUE.equals(found.get().getIsActive())) {
                KnowledgeDocument doc = found.get();
                AiIngestResponse res = aiClient.ingestDocument(new AiDocumentIngestRequest(
                        sourceId, AiDocumentSyncService.SOURCE_TYPE, doc.getTitle(), doc.getBody(),
                        doc.getCustomerId() != null ? doc.getCustomerId().toString() : null), correlationId);
                if ("FAILED".equals(res.status())) {
                    throw new IntegrationJobExecutionException("AI ingest failed: " + res.message(), false);
                }
                jobService.markSucceeded(job, "INGESTED:" + res.chunksIndexed());
                log.info("AI document sync — ingested [documentId={}, chunks={}]", doc.getId(), res.chunksIndexed());
            } else {
                aiClient.deleteSource(AiDocumentSyncService.SOURCE_TYPE, sourceId, correlationId);
                jobService.markSucceeded(job, "REMOVED");
                log.info("AI document sync — removed from index [documentId={}]", payload.documentId());
            }
        } catch (AiServiceUnavailableException ex) {
            boolean clientError = ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
            throw new IntegrationJobExecutionException(ex.getMessage(), clientError, ex);
        }
    }
}
