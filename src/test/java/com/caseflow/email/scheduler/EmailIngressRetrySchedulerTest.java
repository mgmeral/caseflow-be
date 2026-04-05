package com.caseflow.email.scheduler;

import com.caseflow.email.service.EmailIngressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailIngressRetrySchedulerTest {

    @Mock
    private EmailIngressService ingressService;

    private EmailIngressRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EmailIngressRetryScheduler(ingressService, 5);
    }

    // ── processReceivedEvents ─────────────────────────────────────────────────

    @Test
    void processReceivedEvents_callsClaimThenProcessForEachId() {
        when(ingressService.claimReceivedBatch(20)).thenReturn(List.of(1L, 2L, 3L));

        scheduler.processReceivedEvents();

        verify(ingressService).claimReceivedBatch(20);
        verify(ingressService).processEvent(1L);
        verify(ingressService).processEvent(2L);
        verify(ingressService).processEvent(3L);
    }

    @Test
    void processReceivedEvents_doesNotCallProcessEvent_whenNothingClaimed() {
        when(ingressService.claimReceivedBatch(20)).thenReturn(List.of());

        scheduler.processReceivedEvents();

        verify(ingressService).claimReceivedBatch(20);
        verify(ingressService, never()).processEvent(any(Long.class));
    }

    @Test
    void processReceivedEvents_continuesProcessing_whenOneEventFails() {
        when(ingressService.claimReceivedBatch(20)).thenReturn(List.of(10L, 11L));
        doThrow(new RuntimeException("boom")).when(ingressService).processEvent(10L);

        scheduler.processReceivedEvents();

        verify(ingressService).processEvent(10L);
        verify(ingressService).processEvent(11L);
    }

    // ── retryFailedEvents ─────────────────────────────────────────────────────

    @Test
    void retryFailedEvents_callsClaimThenProcessForEachId() {
        when(ingressService.claimFailedBatch(5, 20)).thenReturn(List.of(7L, 8L));

        scheduler.retryFailedEvents();

        verify(ingressService).claimFailedBatch(5, 20);
        verify(ingressService).processEvent(7L);
        verify(ingressService).processEvent(8L);
    }

    @Test
    void retryFailedEvents_doesNotCallProcessEvent_whenNothingClaimed() {
        when(ingressService.claimFailedBatch(5, 20)).thenReturn(List.of());

        scheduler.retryFailedEvents();

        verify(ingressService, never()).processEvent(any(Long.class));
    }
}
