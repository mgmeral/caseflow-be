package com.caseflow.email.scheduler;

import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.email.domain.DispatchFailureCategory;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailMetrics;
import com.caseflow.email.service.SmtpEmailSender;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketSystemTransitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundDispatchSchedulerTest {

    @Mock private OutboundEmailDispatchRepository dispatchRepository;
    @Mock private EmailDispatchService dispatchService;
    @Mock private EmailMailboxRepository mailboxRepository;
    @Mock private SmtpEmailSender smtpSender;
    @Mock private EmailMetrics metrics;
    @Mock private TicketSystemTransitionService systemTransitionService;
    @Mock private TicketHistoryService historyService;

    private OutboundDispatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        // Manually construct to control maxAttempts (final field, @Value not injectable by Mockito)
        scheduler = new OutboundDispatchScheduler(
                dispatchRepository, dispatchService, mailboxRepository,
                smtpSender, metrics, systemTransitionService, historyService, 3);
        // No mailbox by default — use global sender path
        lenient().when(mailboxRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(mailboxRepository.findByAddress(any())).thenReturn(Optional.empty());
    }

    // ── System transition fires only after confirmed send ─────────────────────

    @Test
    void sendPending_appliesOutboundReplyTransition_afterSuccessfulSend() {
        OutboundEmailDispatch dispatch = buildDispatch(1L, 42L);
        when(dispatchRepository.findAndLockPending(anyInt())).thenReturn(List.of(dispatch));

        scheduler.sendPending();

        verify(systemTransitionService).applyOutboundReplyTransition(eq(42L), eq(1L));
    }

    @Test
    void sendPending_doesNotApplyTransition_whenSendFails() {
        OutboundEmailDispatch dispatch = buildDispatch(2L, 42L);
        when(dispatchRepository.findAndLockPending(anyInt())).thenReturn(List.of(dispatch));
        doThrow(new EmailDispatchException("connection refused", DispatchFailureCategory.TLS_FAILURE))
                .when(smtpSender).send(eq(dispatch), any());

        scheduler.sendPending();

        verify(systemTransitionService, never()).applyOutboundReplyTransition(anyLong(), anyLong());
    }

    @Test
    void sendPending_doesNotApplyTransition_whenTicketIdIsNull() {
        OutboundEmailDispatch dispatch = buildDispatch(3L, null);
        when(dispatchRepository.findAndLockPending(anyInt())).thenReturn(List.of(dispatch));

        scheduler.sendPending();

        verify(systemTransitionService, never()).applyOutboundReplyTransition(anyLong(), anyLong());
    }

    // ── markSent called before transition ─────────────────────────────────────

    @Test
    void sendPending_callsMarkSent_beforeApplyingTransition() {
        OutboundEmailDispatch dispatch = buildDispatch(5L, 77L);
        when(dispatchRepository.findAndLockPending(anyInt())).thenReturn(List.of(dispatch));

        scheduler.sendPending();

        var inOrder = org.mockito.Mockito.inOrder(dispatchService, systemTransitionService);
        inOrder.verify(dispatchService).markSent(dispatch);
        inOrder.verify(systemTransitionService).applyOutboundReplyTransition(eq(77L), eq(5L));
    }

    // ── Inactive mailbox → permanently failed, no send, no transition ─────────

    @Test
    void sendPending_permanentlyFails_whenMailboxInactive() {
        OutboundEmailDispatch dispatch = buildDispatch(4L, 10L);
        dispatch.setMailboxId(20L);
        when(dispatchRepository.findAndLockPending(anyInt())).thenReturn(List.of(dispatch));

        EmailMailbox inactive = new EmailMailbox();
        ReflectionTestUtils.setField(inactive, "id", 20L);
        inactive.setIsActive(false);
        when(mailboxRepository.findById(20L)).thenReturn(Optional.of(inactive));

        scheduler.sendPending();

        verify(dispatchService).markPermanentlyFailed(eq(dispatch), anyString(),
                eq(DispatchFailureCategory.MAILBOX_INACTIVE));
        verify(smtpSender, never()).send(any(OutboundEmailDispatch.class), any());
        verify(systemTransitionService, never()).applyOutboundReplyTransition(anyLong(), anyLong());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OutboundEmailDispatch buildDispatch(Long id, Long ticketId) {
        OutboundEmailDispatch d = new OutboundEmailDispatch();
        ReflectionTestUtils.setField(d, "id", id);
        d.setTicketId(ticketId);
        d.setFromAddress("support@caseflow.dev");
        d.setToAddress("customer@example.com");
        d.setSubject("Re: Issue");
        d.setAttempts(0);
        return d;
    }
}
