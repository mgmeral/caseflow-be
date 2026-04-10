package com.caseflow.email.scheduled.service;

import com.caseflow.common.exception.EmailOperationException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.ReplyThreadContext;
import com.caseflow.email.service.ReplyThreadContextResolver;
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
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private ReplyThreadContextResolver threadContextResolver;

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
    void scheduleEmail_throwsScheduleTimeInvalid_whenSendNotBeforeIsInPast() {
        UUID publicId = UUID.randomUUID();
        Instant pastTime = Instant.now().minusSeconds(60);

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, pastTime, 42L))
                .isInstanceOf(EmailOperationException.class)
                .hasMessageContaining("future")
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("SCHEDULE_TIME_INVALID");
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
    void scheduleEmail_throwsMailboxNotActive_whenMailboxInactive() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(1L)).thenReturn(Optional.of(inactiveMailbox));

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, Instant.now().plusSeconds(3600), 42L))
                .isInstanceOf(EmailOperationException.class)
                .hasMessageContaining("inactive")
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("MAILBOX_NOT_ACTIVE");
    }

    @Test
    void scheduleEmail_createsDispatch_forValidRequest() {
        UUID publicId = UUID.randomUUID();
        Instant sendAt = Instant.now().plus(2, ChronoUnit.HOURS);
        ReplyThreadContext threadCtx = new ReplyThreadContext("to@example.com", null, null, null);

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(1L)).thenReturn(Optional.of(activeMailbox));
        when(threadContextResolver.resolveForTicket(isNull(), eq("to@example.com"), any())).thenReturn(threadCtx);
        when(dispatchService.enqueueScheduled(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(),
                eq(sendAt), any(), any(), anyBoolean())).thenReturn(dispatch);

        OutboundEmailDispatch result = service.scheduleEmail(publicId, 1L, "to@example.com",
                "Subject", "body", null, sendAt, 42L);

        assertThat(result).isSameAs(dispatch);
        verify(historyService).recordScheduledEmailCreated(any(), any(), any(), anyString(), eq(sendAt), eq(42L));
    }

    @Test
    void scheduleEmail_throwsReplyTargetUnresolvable_whenNeitherSourceEventIdNorToAddress() {
        UUID publicId = UUID.randomUUID();
        Instant sendAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L,
                null, null,
                "Subject", "body", null,
                sendAt, 42L, null, null, false))
                .isInstanceOf(EmailOperationException.class)
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("REPLY_TARGET_UNRESOLVABLE");
    }

    @Test
    void scheduleEmail_withSourceEventId_populatesThreadContextFromResolver() {
        UUID publicId = UUID.randomUUID();
        Instant sendAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Long sourceEventId = 77L;
        ReplyThreadContext threadCtx = new ReplyThreadContext(
                "customer@example.com",
                "<original@mail.example.com>",
                "<original@mail.example.com>",
                sourceEventId);

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(1L)).thenReturn(Optional.of(activeMailbox));
        when(threadContextResolver.resolveForTicket(eq(sourceEventId), isNull(), any())).thenReturn(threadCtx);
        when(dispatchService.enqueueScheduled(
                any(), any(), eq(sourceEventId), any(),
                any(), eq("customer@example.com"), eq("customer@example.com"),
                any(), any(), any(),
                eq("<original@mail.example.com>"), eq("<original@mail.example.com>"),
                eq(sendAt), any(), any(), anyBoolean())).thenReturn(dispatch);

        OutboundEmailDispatch result = service.scheduleEmail(publicId, 1L,
                sourceEventId, null,
                "Re: Your ticket", "body text", null,
                sendAt, 42L, null, null, false);

        assertThat(result).isSameAs(dispatch);
        verify(threadContextResolver).resolveForTicket(eq(sourceEventId), isNull(), any());
    }

    @Test
    void scheduleEmail_throwsSourceEventNotForTicket_whenResolverRejectsOwnership() {
        UUID publicId = UUID.randomUUID();
        Instant sendAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Long sourceEventId = 55L;

        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(1L)).thenReturn(Optional.of(activeMailbox));
        when(threadContextResolver.resolveForTicket(eq(sourceEventId), isNull(), any()))
                .thenThrow(new EmailOperationException("SOURCE_EVENT_NOT_FOR_TICKET",
                        "Source event 55 does not belong to ticket null"));

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 1L,
                sourceEventId, null,
                "Subject", "body", null,
                sendAt, 42L, null, null, false))
                .isInstanceOf(EmailOperationException.class)
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("SOURCE_EVENT_NOT_FOR_TICKET");
    }

    @Test
    void scheduleEmail_throwsMailboxNotFound_whenMailboxMissing() {
        UUID publicId = UUID.randomUUID();
        when(ticketRepository.findByPublicId(publicId)).thenReturn(Optional.of(openTicket));
        when(mailboxRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scheduleEmail(publicId, 99L, "to@example.com",
                "Subject", "body", null, Instant.now().plusSeconds(3600), 42L))
                .isInstanceOf(EmailOperationException.class)
                .extracting(e -> ((EmailOperationException) e).getCode())
                .isEqualTo("MAILBOX_NOT_FOUND");
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
