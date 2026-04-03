package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.ticket.domain.Ticket;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailReplyServiceTest {

    @Mock private EmailDispatchService dispatchService;
    @Mock private TicketRepository ticketRepository;
    @Mock private EmailIngressEventRepository ingressEventRepository;
    @Mock private TicketHistoryService historyService;
    @Mock private EmailMetrics metrics;
    @Mock private MailTemplateService mailTemplateService;

    @InjectMocks
    private EmailReplyService sut;

    private Ticket ticket;
    private EmailIngressEvent sourceEvent;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        lenient().when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        sourceEvent = new EmailIngressEvent();
        sourceEvent.setRawFrom("customer@example.com");
        sourceEvent.setMessageId("<msg123@example.com>");
        sourceEvent.setReceivedAt(Instant.now());
        sourceEvent.setRawReferences(null);

        var dispatch = mock(OutboundEmailDispatch.class);
        lenient().when(dispatch.getId()).thenReturn(99L);
        lenient().when(dispatch.getResolvedToAddress()).thenReturn("customer@example.com");
        lenient().when(dispatch.getMailboxId()).thenReturn(2L);
        lenient().when(dispatchService.enqueue(anyLong(), anyLong(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any(), any())).thenReturn(dispatch);

        lenient().when(mailTemplateService.findActiveByCode(anyString())).thenReturn(java.util.Optional.empty());
    }

    // ── Reply target derivation from source event ─────────────────────────────

    @Test
    void sendReply_derivesToAddressFromSourceEvent_fromHeader() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), eq(10L), eq(42L),
                eq("support@caseflow.dev"),
                eq("customer@example.com"),
                eq("customer@example.com"),
                anyString(), any(), any(), any(), any());
    }

    @Test
    void sendReply_derivesToAddressFromSourceEvent_replyToHeaderTakesPrecedence() {
        sourceEvent.setRawReplyTo("reply-target@example.com");
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), eq(10L), eq(42L),
                eq("support@caseflow.dev"),
                eq("reply-target@example.com"),
                eq("reply-target@example.com"),
                anyString(), any(), any(), any(), any());
    }

    @Test
    void sendReply_stripsDisplayNameFromReplyTo() {
        sourceEvent.setRawReplyTo("John Customer <john@bigcorp.com>");
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), eq(10L), eq(42L),
                eq("support@caseflow.dev"),
                eq("john@bigcorp.com"),
                eq("john@bigcorp.com"),
                anyString(), any(), any(), any(), any());
    }

    @Test
    void sendReply_usesToAddressOverride_whenNoSourceEventId() {
        sut.sendReply(1L, 2L, null, "manual@example.com", "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), isNull(), eq(42L),
                eq("support@caseflow.dev"),
                eq("manual@example.com"),
                eq("manual@example.com"),
                anyString(), any(), any(), any(), any());
    }

    @Test
    void sendReply_throws_whenNeitherSourceEventNorToAddress() {
        assertThatThrownBy(() ->
                sut.sendReply(1L, 2L, null, null, "support@caseflow.dev",
                        "Re: Issue", "body", null, null, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceEventId or toAddress");
    }

    @Test
    void sendReply_throws_whenSourceEventNotFound() {
        when(ingressEventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                sut.sendReply(1L, 2L, 999L, null, "support@caseflow.dev",
                        "Re: Issue", "body", null, null, 42L))
                .isInstanceOf(IngressEventNotFoundException.class);
    }

    // ── No premature system transition ────────────────────────────────────────

    @Test
    void sendReply_doesNotApplySystemTransition_atEnqueueTime() {
        // The WAITING_CUSTOMER transition is applied by OutboundDispatchScheduler
        // after SMTP send succeeds — NOT here at enqueue time.
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        // sendReply must complete without injecting TicketSystemTransitionService
        // (it's not in the constructor — if it were called, a NullPointerException would fail this test)
        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);
    }

    // ── Returns dispatch info ─────────────────────────────────────────────────

    @Test
    void sendReply_returnsDispatch_withResolvedAddress() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        OutboundEmailDispatch result = sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);
    }

    // ── History recording ─────────────────────────────────────────────────────

    @Test
    void sendReply_recordsHistoryEvent() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        verify(historyService).record(eq(1L), eq("CUSTOMER_REPLY_QUEUED"), eq(42L), anyString());
    }

    // ── RFC 2822 threading header derivation ──────────────────────────────────

    @Test
    void sendReply_derivesInReplyTo_fromSourceEventMessageId_whenNotExplicit() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        // inReplyToMessageId should be "<msg123@example.com>" from source event
        verify(dispatchService).enqueue(
                eq(1L), eq(2L), eq(10L), eq(42L),
                eq("support@caseflow.dev"),
                eq("customer@example.com"),
                eq("customer@example.com"),
                anyString(), any(), any(),
                eq("<msg123@example.com>"),  // derived inReplyTo
                eq("<msg123@example.com>")); // references = just the parent when no prior chain
    }

    @Test
    void sendReply_buildsReferencesChain_whenPriorRefsExist() {
        sourceEvent.setRawReplyTo(null);
        sourceEvent.setRawReferences("<thread-start@example.com>|<thread-mid@example.com>");
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sut.sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        // References chain: prior refs (pipe → space) + source messageId
        String expectedRefs = "<thread-start@example.com> <thread-mid@example.com> <msg123@example.com>";
        verify(dispatchService).enqueue(
                eq(1L), eq(2L), eq(10L), eq(42L),
                eq("support@caseflow.dev"),
                eq("customer@example.com"),
                eq("customer@example.com"),
                anyString(), any(), any(),
                eq("<msg123@example.com>"),
                eq(expectedRefs));
    }

    @Test
    void sendReply_passesNullReferences_whenNoSourceEvent() {
        sut.sendReply(1L, 2L, null, "manual@example.com", "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), isNull(), eq(42L),
                eq("support@caseflow.dev"),
                eq("manual@example.com"),
                eq("manual@example.com"),
                anyString(), any(), any(),
                isNull(),   // no inReplyTo when no source event
                isNull());  // no references when no source event
    }

    // ── Template rendering — HTML body handling ───────────────────────────────

    @Test
    void sendReply_usesHtmlBody_forHtmlTemplate_whenHtmlBodyProvided() {
        // Arrange: DB template is present
        com.caseflow.email.domain.MailTemplate tpl = new com.caseflow.email.domain.MailTemplate();
        tpl.setCode("CUSTOMER_REPLY");
        tpl.setHtmlTemplate("<html>{replyBody}</html>");
        tpl.setPlainTextTemplate("{replyBody}");
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(java.util.Optional.of(tpl));
        when(mailTemplateService.substitute(eq("<html>{replyBody}</html>"), anyString(), any(), any(), any(), any()))
                .thenReturn("<html>rendered html</html>");
        when(mailTemplateService.substitute(eq("{replyBody}"), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered text");
        when(dispatchService.enqueue(anyLong(), anyLong(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(mock(OutboundEmailDispatch.class));

        sut.sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "plain text", "<b>html content</b>", null, 1L);

        // escapeHtml must NOT be called when htmlBody is provided
        verify(mailTemplateService, org.mockito.Mockito.never())
                .escapeHtml(anyString());
    }

    @Test
    void sendReply_escapesTextBody_forHtmlTemplate_whenHtmlBodyAbsent() {
        // Arrange: DB template present, no htmlBody provided
        com.caseflow.email.domain.MailTemplate tpl = new com.caseflow.email.domain.MailTemplate();
        tpl.setCode("CUSTOMER_REPLY");
        tpl.setHtmlTemplate("<html>{replyBody}</html>");
        tpl.setPlainTextTemplate("{replyBody}");
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(java.util.Optional.of(tpl));
        when(mailTemplateService.escapeHtml("plain text with <tags>")).thenReturn("plain text with &lt;tags&gt;");
        when(mailTemplateService.substitute(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered");
        when(dispatchService.enqueue(anyLong(), anyLong(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(mock(OutboundEmailDispatch.class));

        sut.sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "plain text with <tags>", null, null, 1L);

        // escapeHtml MUST be called with the textBody
        verify(mailTemplateService).escapeHtml("plain text with <tags>");
    }
}
