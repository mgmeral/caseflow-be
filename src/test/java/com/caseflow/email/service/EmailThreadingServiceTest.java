package com.caseflow.email.service;

import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailThreadingServiceTest {

    @Mock
    private EmailDocumentRepository emailDocumentRepository;

    @InjectMocks
    private EmailThreadingService threadingService;

    // ── resolveThreadKey ──────────────────────────────────────────────────────

    @Test
    void resolveThreadKey_inheritsFromParent_whenInReplyToFound() {
        EmailDocument parent = docWithThreadKey("<msg-001@example.com>", "thread-abc");
        when(emailDocumentRepository.findByMessageId("<msg-001@example.com>"))
                .thenReturn(Optional.of(parent));

        String result = threadingService.resolveThreadKey("<msg-001@example.com>", null, "<msg-002@example.com>");
        assertEquals("thread-abc", result);
    }

    @Test
    void resolveThreadKey_inheritsFromReferences_whenInReplyToNotFound() {
        when(emailDocumentRepository.findByMessageId("<msg-001@example.com>"))
                .thenReturn(Optional.empty());
        EmailDocument refDoc = docWithThreadKey("<ref-msg@example.com>", "thread-xyz");
        when(emailDocumentRepository.findByMessageId("<ref-msg@example.com>"))
                .thenReturn(Optional.of(refDoc));

        String result = threadingService.resolveThreadKey(
                "<msg-001@example.com>",
                List.of("<ref-msg@example.com>"),
                "<msg-002@example.com>");
        assertEquals("thread-xyz", result);
    }

    @Test
    void resolveThreadKey_usesOwnMessageId_whenNoParentFound() {
        // inReplyTo and references are null — no repository calls made
        String result = threadingService.resolveThreadKey(null, null, "<msg-001@example.com>");
        assertEquals("<msg-001@example.com>", result);
    }

    @Test
    void resolveThreadKey_generatesUUID_whenMessageIdIsNull() {
        String result = threadingService.resolveThreadKey(null, null, null);
        assertNotNull(result);
        assertNotNull(result); // UUID or some non-null key
    }

    // ── resolveTicketId ───────────────────────────────────────────────────────

    @Test
    void resolveTicketId_returnsTicketId_whenInReplyToHasTicket() {
        EmailDocument parent = docWithTicketId("<msg-001@example.com>", 42L);
        when(emailDocumentRepository.findByMessageId("<msg-001@example.com>"))
                .thenReturn(Optional.of(parent));

        Optional<Long> result = threadingService.resolveTicketId("<msg-001@example.com>", null);
        assertTrue(result.isPresent());
        assertEquals(42L, result.get());
    }

    @Test
    void resolveTicketId_returnsEmpty_whenNoParentHasTicket() {
        when(emailDocumentRepository.findByMessageId("<msg-001@example.com>"))
                .thenReturn(Optional.empty());

        Optional<Long> result = threadingService.resolveTicketId("<msg-001@example.com>", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolveTicketId_walksReferences_whenInReplyToMissing() {
        when(emailDocumentRepository.findByMessageId("<msg-001@example.com>"))
                .thenReturn(Optional.empty());
        EmailDocument refDoc = docWithTicketId("<ref@example.com>", 99L);
        when(emailDocumentRepository.findByMessageId("<ref@example.com>"))
                .thenReturn(Optional.of(refDoc));

        Optional<Long> result = threadingService.resolveTicketId(
                "<msg-001@example.com>",
                List.of("<ref@example.com>"));
        assertTrue(result.isPresent());
        assertEquals(99L, result.get());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailDocument docWithThreadKey(String messageId, String threadKey) {
        EmailDocument doc = new EmailDocument();
        doc.setMessageId(messageId);
        doc.setThreadKey(threadKey);
        return doc;
    }

    private EmailDocument docWithTicketId(String messageId, Long ticketId) {
        EmailDocument doc = new EmailDocument();
        doc.setMessageId(messageId);
        doc.setTicketId(ticketId);
        return doc;
    }
}
