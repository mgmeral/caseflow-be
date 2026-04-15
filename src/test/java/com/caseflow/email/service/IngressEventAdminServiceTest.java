package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.common.exception.InvalidIngressEventStateException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailIngressEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngressEventAdminServiceTest {

    @Mock
    private EmailIngressEventRepository eventRepository;

    @Mock
    private EmailIngressService ingressService;

    @InjectMocks
    private IngressEventAdminService adminService;

    private EmailIngressEvent failedEvent;
    private EmailIngressEvent processedEvent;
    private EmailIngressEvent quarantinedEvent;
    private EmailIngressEvent receivedEvent;

    @BeforeEach
    void setUp() {
        failedEvent = buildEvent(1L, IngressEventStatus.FAILED);
        processedEvent = buildEvent(2L, IngressEventStatus.PROCESSED);
        quarantinedEvent = buildEvent(3L, IngressEventStatus.QUARANTINED);
        receivedEvent = buildEvent(4L, IngressEventStatus.RECEIVED);
    }

    // ── findOrThrow ────────────────────────────────────────────────────────────

    @Test
    void getById_throwsIngressEventNotFoundException_whenNotFound() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getById(99L))
                .isInstanceOf(IngressEventNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── retryEvent ─────────────────────────────────────────────────────────────

    @Test
    void retryEvent_throwsInvalidState_whenNotFailed() {
        when(eventRepository.findById(4L)).thenReturn(Optional.of(receivedEvent));

        assertThatThrownBy(() -> adminService.retryEvent(4L))
                .isInstanceOf(InvalidIngressEventStateException.class)
                .hasMessageContaining("retried")
                .hasMessageContaining("RECEIVED")
                .hasMessageContaining("FAILED");

        verify(ingressService, never()).processEvent(4L);
    }

    @Test
    void retryEvent_throwsInvalidState_whenQuarantined() {
        when(eventRepository.findById(3L)).thenReturn(Optional.of(quarantinedEvent));

        assertThatThrownBy(() -> adminService.retryEvent(3L))
                .isInstanceOf(InvalidIngressEventStateException.class);

        verify(ingressService, never()).processEvent(3L);
    }

    @Test
    void retryEvent_throwsInvalidState_whenAlreadyProcessed() {
        when(eventRepository.findById(2L)).thenReturn(Optional.of(processedEvent));

        assertThatThrownBy(() -> adminService.retryEvent(2L))
                .isInstanceOf(InvalidIngressEventStateException.class);
    }

    @Test
    void retryEvent_delegatesToIngressService_whenStatusIsFailed() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(failedEvent));

        adminService.retryEvent(1L);

        verify(ingressService).processEvent(1L);
    }

    // ── quarantineEvent ────────────────────────────────────────────────────────

    @Test
    void quarantineEvent_throwsInvalidState_whenAlreadyProcessed() {
        when(eventRepository.findById(2L)).thenReturn(Optional.of(processedEvent));

        assertThatThrownBy(() -> adminService.quarantineEvent(2L, "reason"))
                .isInstanceOf(InvalidIngressEventStateException.class)
                .hasMessageContaining("quarantined")
                .hasMessageContaining("PROCESSED");

        verify(ingressService, never()).quarantineEvent(2L, "reason");
    }

    @Test
    void quarantineEvent_delegatesToIngressService_whenStatusIsFailed() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(failedEvent));

        adminService.quarantineEvent(1L, "suspected spam");

        verify(ingressService).quarantineEvent(1L, "suspected spam");
    }

    @Test
    void quarantineEvent_delegatesToIngressService_whenStatusIsReceived() {
        when(eventRepository.findById(4L)).thenReturn(Optional.of(receivedEvent));

        adminService.quarantineEvent(4L, null);

        verify(ingressService).quarantineEvent(4L, null);
    }

    // ── releaseEvent ───────────────────────────────────────────────────────────

    @Test
    void releaseEvent_throwsInvalidState_whenNotQuarantined() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(failedEvent));

        assertThatThrownBy(() -> adminService.releaseEvent(1L))
                .isInstanceOf(InvalidIngressEventStateException.class)
                .hasMessageContaining("released")
                .hasMessageContaining("FAILED")
                .hasMessageContaining("QUARANTINED");

        verify(ingressService, never()).releaseEvent(1L);
    }

    @Test
    void releaseEvent_throwsInvalidState_whenAlreadyProcessed() {
        when(eventRepository.findById(2L)).thenReturn(Optional.of(processedEvent));

        assertThatThrownBy(() -> adminService.releaseEvent(2L))
                .isInstanceOf(InvalidIngressEventStateException.class);
    }

    @Test
    void releaseEvent_delegatesToIngressService_whenQuarantined() {
        when(eventRepository.findById(3L)).thenReturn(Optional.of(quarantinedEvent));

        adminService.releaseEvent(3L);

        verify(ingressService).releaseEvent(3L);
    }

    // ── processEvent ───────────────────────────────────────────────────────────

    @Test
    void processEvent_throwsInvalidState_whenAlreadyProcessed() {
        when(eventRepository.findById(2L)).thenReturn(Optional.of(processedEvent));

        assertThatThrownBy(() -> adminService.processEvent(2L))
                .isInstanceOf(InvalidIngressEventStateException.class)
                .hasMessageContaining("PROCESSED");

        verify(ingressService, never()).processEvent(2L);
    }

    @Test
    void processEvent_releasesQuarantinedFirst_thenProcesses() {
        when(eventRepository.findById(3L)).thenReturn(Optional.of(quarantinedEvent));

        adminService.processEvent(3L);

        verify(ingressService).releaseEvent(3L);
        verify(ingressService).processEvent(3L);
    }

    @Test
    void processEvent_processesDirectly_whenStatusIsReceived() {
        when(eventRepository.findById(4L)).thenReturn(Optional.of(receivedEvent));

        adminService.processEvent(4L);

        verify(ingressService, never()).releaseEvent(4L);
        verify(ingressService).processEvent(4L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EmailIngressEvent buildEvent(Long id, IngressEventStatus status) {
        EmailIngressEvent event = new EmailIngressEvent();
        event.setStatus(status);
        try {
            var idField = EmailIngressEvent.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return event;
    }
}
