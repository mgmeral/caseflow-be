package com.caseflow.email.scheduled.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledEmailServiceTest {

    @Mock private EmailDispatchService dispatchService;
    @Mock private EmailMailboxRepository mailboxRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private OutboundEmailDispatchRepository dispatchRepository;
    @Mock private TicketHistoryService historyService;

    @InjectMocks
    private ScheduledEmailService service;

    private Ticket openTicket;
    private Ticket closedTicket;
    private EmailMailbox activeMailbox;
    private EmailMailbox inactiveMailbox;
    private OutboundEmailDispatch dispatch;

    @BeforeEach
    void setUp() {
        openTicket = new Ticket();
        openTicket.setTicketNo("TKT-001");
        openTicket.setStatus(TicketStatus.IN_PROGRESS);
        openTicket.setPriority(TicketPriority.MEDIUM);

        closedTicket = new Ticket();
        closedTicket.setTicketNo("TKT-002");
        closedTicket.setStatus(TicketStatus.CLOSED);

        activeMailbox = new EmailMailbox();
        activeMailbox.setIsActive(true);
        activeMailbox.setSmtpHost("smtp.example.com");
        activeMailbox.setAddress("support@example.com");

        inactiveMailbox = new EmailMailbox();
        inactiveMailbox.setIsActive(false);
        inactiveMailbox.setAddress("support@example.com");

        dispatch = new OutboundEmailDispatch();
        dispatch.setIsScheduledSend(true);
        dispatch.setStatus(com.caseflow.email.domain.DispatchStatus.PENDING);
    }

    // ── scheduleEmail ─────────────────────────────────────────────────────────

    @Test
    void scheduleEmail_throwsIllegalArgument_whenSendNotBeforeIsInPast() {
        UUID publicId = UUID.randomUUID();
        Instant pastTime = Instant.now().minusSeconds(60);

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, pastTime, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void scheduleEmail_throwsTicketNotFound_whenTicketMissing() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, Instant.now().plusSeconds(3600), 42L))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void scheduleEmail_throwsIllegalState_whenTicketClosed() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(closedTicket));

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, Instant.now().plusSeconds(3600), 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void scheduleEmail_throwsIllegalState_whenMailboxInactive() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(1L)).thenReturn(Optional.of(inactiveMailbox));

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, Instant.now().plusSeconds(3600), 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void scheduleEmail_createsDispatch_forValidRequest() {
        UUID publicId = UUID.randomUUID();
        Instant sendAt = Instant.now().plus(2, ChronoUnit.HOURS);

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(1L)).thenReturn(Optional.of(activeMailbox));
        when(dispatchService.enqueueScheduled(any(), any(), any(), any(), any(), any(), any(), any(),
                eq(sendAt), any(), any(), anyBoolean())).thenReturn(dispatch);

        OutboundEmailDispatch result = service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, sendAt, 42L);

        assertThat(result).isSameAs(dispatch);
        verify(historyService).recordScheduledEmailCreated(any(), any(), any(), anyString(), eq(sendAt), eq(42L));
    }

    // ── cancelScheduledEmail ──────────────────────────────────────────────────

    @Test
    void cancelScheduledEmail_throwsIllegalArgument_whenDispatchNotFound() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(dispatchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelScheduledEmail(publicId, 99L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void cancelScheduledEmail_throwsIllegalArgument_whenDispatchNotScheduledSend() {
        UUID publicId = UUID.randomUUID();
        dispatch.setIsScheduledSend(false);
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(dispatchRepository.findById(1L)).thenReturn(Optional.of(dispatch));

        assertThatThrownBy(() -> service.cancelScheduledEmail(publicId, 1L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a scheduled send");
    }

    @Test
    void cancelScheduledEmail_cancelsPendingDispatch() {
        UUID publicId = UUID.randomUUID();
        // Make ticketId match
        dispatch.setIsScheduledSend(true);
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(dispatchRepository.findById(1L)).thenReturn(Optional.of(dispatch));

        // dispatch.ticketId is null, openTicket.id is null — both null = equals null check passes
        // In real entity, id is set by DB. For test, ticket has null id, dispatch has null ticketId.
        // The service checks ticket.getId().equals(dispatch.getTicketId()) — both null, equals returns true.
        service.cancelScheduledEmail(publicId, 1L, 42L);

        verify(dispatchService).markCanceled(dispatch);
        verify(historyService).recordScheduledEmailCanceled(any(), any(), any(), eq(42L));
    }
}
