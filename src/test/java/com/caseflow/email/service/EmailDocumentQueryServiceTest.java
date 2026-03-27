package com.caseflow.email.service;

import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDocumentQueryServiceTest {

    @Mock
    private EmailDocumentRepository emailDocumentRepository;

    @InjectMocks
    private EmailDocumentQueryService emailDocumentQueryService;

    @Test
    void findById_returnsDocument_whenFound() {
        EmailDocument doc = buildDoc("doc-1", 100L, "thread-a");
        when(emailDocumentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        Optional<EmailDocument> result = emailDocumentQueryService.findById("doc-1");

        assertThat(result).isPresent();
        assertThat(result.get().getTicketId()).isEqualTo(100L);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        when(emailDocumentRepository.findById("missing")).thenReturn(Optional.empty());

        Optional<EmailDocument> result = emailDocumentQueryService.findById("missing");
        assertThat(result).isEmpty();
    }

    @Test
    void findByTicketId_returnsMatchingDocuments() {
        EmailDocument d1 = buildDoc("d1", 200L, "thread-b");
        EmailDocument d2 = buildDoc("d2", 200L, "thread-b");
        when(emailDocumentRepository.findByTicketId(200L)).thenReturn(List.of(d1, d2));

        List<EmailDocument> results = emailDocumentQueryService.findByTicketId(200L);
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.getTicketId().equals(200L));
    }

    @Test
    void findByTicketId_returnsEmpty_whenNoDocuments() {
        when(emailDocumentRepository.findByTicketId(999L)).thenReturn(List.of());

        List<EmailDocument> results = emailDocumentQueryService.findByTicketId(999L);
        assertThat(results).isEmpty();
    }

    @Test
    void findByThreadKey_returnsMatchingDocuments() {
        EmailDocument d1 = buildDoc("d1", 300L, "thread-c");
        when(emailDocumentRepository.findByThreadKey("thread-c")).thenReturn(List.of(d1));

        List<EmailDocument> results = emailDocumentQueryService.findByThreadKey("thread-c");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getThreadKey()).isEqualTo("thread-c");
    }

    private EmailDocument buildDoc(String id, Long ticketId, String threadKey) {
        EmailDocument doc = new EmailDocument();
        doc.setMessageId("<" + id + "@test.com>");
        doc.setThreadKey(threadKey);
        doc.setSubject("Subject " + id);
        doc.setFrom("sender@test.com");
        doc.setTicketId(ticketId);
        doc.setReceivedAt(Instant.now());
        doc.setParsedAt(Instant.now());
        return doc;
    }
}
