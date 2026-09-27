package com.caseflow.knowledge.service;

import com.caseflow.ai.service.AiDocumentSyncService;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.knowledge.api.dto.KnowledgeDocumentRequest;
import com.caseflow.knowledge.domain.KnowledgeDocument;
import com.caseflow.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentServiceTest {

    private final KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AiDocumentSyncService syncService = mock(AiDocumentSyncService.class);
    private final KnowledgeDocumentService service =
            new KnowledgeDocumentService(repository, customerRepository, syncService);

    @Test
    void create_savesActiveDocument_andEnqueuesIndexSync() {
        when(customerRepository.existsById(42L)).thenReturn(true);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        KnowledgeDocument saved = service.create(
                new KnowledgeDocumentRequest(" Refund policy ", "Refunds within 30 days.", " ", 42L, null), 7L);

        assertThat(saved.getTitle()).isEqualTo("Refund policy");
        assertThat(saved.getCategory()).isNull();
        assertThat(saved.getCustomerId()).isEqualTo(42L);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
        verify(syncService).requestSync(saved, "USER", 7L);
    }

    @Test
    void create_withUnknownCustomer_isRejected_andNothingIsSaved() {
        when(customerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(new KnowledgeDocumentRequest("t", "b", null, 99L, true), 7L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
        verify(syncService, never()).requestSync(any(), anyString(), any());
    }

    @Test
    void update_withoutActiveFlag_keepsState() {
        KnowledgeDocument existing = new KnowledgeDocument();
        existing.setIsActive(false);
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        KnowledgeDocument saved = service.update(3L, new KnowledgeDocumentRequest("t", "b", null, null, null), 7L);

        assertThat(saved.getIsActive()).isFalse();
        verify(syncService).requestSync(saved, "USER", 7L);
    }

    @Test
    void delete_enqueuesSyncBeforeDeleting_soThePublicIdSurvives() {
        KnowledgeDocument existing = new KnowledgeDocument();
        when(repository.findById(3L)).thenReturn(Optional.of(existing));

        service.delete(3L, 7L);

        InOrder order = inOrder(syncService, repository);
        order.verify(syncService).requestSync(existing, "USER", 7L);
        order.verify(repository).delete(existing);
    }
}
