package com.caseflow.ai.service;

import com.caseflow.ai.client.CaseflowAiClient;
import com.caseflow.ai.client.dto.request.AiDocumentIngestRequest;
import com.caseflow.ai.client.dto.response.AiIngestResponse;
import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobService;
import com.caseflow.knowledge.domain.KnowledgeDocument;
import com.caseflow.knowledge.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiDocumentSyncTest {

    private static final UUID PUBLIC_ID = UUID.fromString("0b7e2c55-9d41-4c8a-a7f2-3e5d6c7b8a90");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationJobService jobService = mock(IntegrationJobService.class);
    private final KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
    private final AiAvailabilityService availability = mock(AiAvailabilityService.class);
    private final CaseflowAiClient aiClient = mock(CaseflowAiClient.class);

    private final AiDocumentSyncService syncService =
            new AiDocumentSyncService(jobService, documentRepository, availability, objectMapper);
    private final AiDocumentSyncProcessor processor =
            new AiDocumentSyncProcessor(aiClient, documentRepository, jobService, objectMapper);

    private static KnowledgeDocument doc(Long customerId, boolean active) {
        KnowledgeDocument d = new KnowledgeDocument();
        try {
            var id = KnowledgeDocument.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(d, 3L);
            var pid = KnowledgeDocument.class.getDeclaredField("publicId");
            pid.setAccessible(true);
            pid.set(d, PUBLIC_ID);
        } catch (Exception e) { throw new RuntimeException(e); }
        d.setTitle("Refund policy");
        d.setBody("Refunds within 30 days.");
        d.setCustomerId(customerId);
        d.setIsActive(active);
        return d;
    }

    private IntegrationJob job(String payload) {
        IntegrationJob job = new IntegrationJob();
        job.setIntegrationType(IntegrationType.AI_DOCUMENT_SYNC);
        job.setPayloadJson(payload);
        return job;
    }

    private String enqueuedPayload(KnowledgeDocument doc) {
        when(availability.isAvailable()).thenReturn(true);
        syncService.requestSync(doc, "USER", 7L);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(jobService).enqueue(eq(IntegrationType.AI_DOCUMENT_SYNC), isNull(), isNull(), any(), isNull(),
                payload.capture(), anyString(), eq("USER"), eq(7L));
        return payload.getValue();
    }

    @Test
    void requestSync_payloadNamesTheDocumentAndItsPublicId() {
        String payload = enqueuedPayload(doc(42L, true));

        assertThat(payload).contains("\"documentId\":3").contains(PUBLIC_ID.toString());
    }

    @Test
    void activeDocument_isIngestedAsPolicy_scopedToItsCustomer() {
        KnowledgeDocument d = doc(42L, true);
        String payload = enqueuedPayload(d);
        when(documentRepository.findById(3L)).thenReturn(Optional.of(d));
        when(aiClient.ingestDocument(any(), anyString()))
                .thenReturn(new AiIngestResponse("j", PUBLIC_ID.toString(), 1, "SUCCESS", "ok"));

        IntegrationJob job = job(payload);
        processor.process(job);

        verify(aiClient).ingestDocument(eq(new AiDocumentIngestRequest(PUBLIC_ID.toString(), "POLICY",
                "Refund policy", "Refunds within 30 days.", "42")), anyString());
        verify(jobService).markSucceeded(job, "INGESTED:1");
    }

    @Test
    void globalDocument_isSentWithoutCustomerId() {
        KnowledgeDocument d = doc(null, true);
        String payload = enqueuedPayload(d);
        when(documentRepository.findById(3L)).thenReturn(Optional.of(d));
        when(aiClient.ingestDocument(any(), anyString()))
                .thenReturn(new AiIngestResponse("j", PUBLIC_ID.toString(), 1, "SUCCESS", "ok"));

        processor.process(job(payload));

        ArgumentCaptor<AiDocumentIngestRequest> sent = ArgumentCaptor.forClass(AiDocumentIngestRequest.class);
        verify(aiClient).ingestDocument(sent.capture(), anyString());
        assertThat(sent.getValue().customerId()).isNull();
    }

    @Test
    void inactiveOrDeletedDocument_isRemovedFromIndex() {
        KnowledgeDocument inactive = doc(42L, false);
        String payload = enqueuedPayload(inactive);
        when(documentRepository.findById(3L)).thenReturn(Optional.of(inactive), Optional.empty());

        processor.process(job(payload));
        processor.process(job(payload));

        verify(aiClient, org.mockito.Mockito.times(2)).deleteSource(eq("POLICY"), eq(PUBLIC_ID.toString()), anyString());
        verify(aiClient, never()).ingestDocument(any(), any());
    }

    @Test
    void unreadablePayload_failsPermanently() {
        assertThatThrownBy(() -> processor.process(job("not json")))
                .isInstanceOfSatisfying(IntegrationJobExecutionException.class, e -> assertThat(e.isPermanent()).isTrue());
    }

    @Test
    void aiDisabled_enqueuesNothing() {
        when(availability.isAvailable()).thenReturn(false);

        assertThat(syncService.requestSync(doc(null, true), "USER", 7L)).isFalse();
        verify(jobService, never()).enqueue(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
