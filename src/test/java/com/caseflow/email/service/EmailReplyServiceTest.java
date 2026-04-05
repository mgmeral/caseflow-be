package com.caseflow.email.service;

import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.MailTemplate;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private OutboundEmailDispatch mockDispatch;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setStatus(TicketStatus.ASSIGNED);
        try {
            // Set ticketNo so template substitution doesn't NPE
            var no = Ticket.class.getDeclaredField("ticketNo");
            no.setAccessible(true);
            no.set(ticket, "TKT-0000001");
            var pid = Ticket.class.getDeclaredField("publicId");
            pid.setAccessible(true);
            pid.set(ticket, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        lenient().when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        sourceEvent = new EmailIngressEvent();
        sourceEvent.setRawFrom("customer@example.com");
        sourceEvent.setMessageId("<msg123@example.com>");
        sourceEvent.setReceivedAt(Instant.now());
        sourceEvent.setRawReferences(null);

        mockDispatch = mock(OutboundEmailDispatch.class);
        lenient().when(mockDispatch.getId()).thenReturn(99L);
        lenient().when(mockDispatch.getResolvedToAddress()).thenReturn("customer@example.com");
        lenient().when(mockDispatch.getMailboxId()).thenReturn(2L);
        lenient().when(dispatchService.enqueue(anyLong(), anyLong(), any(), any(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), any(), any())).thenReturn(mockDispatch);

        lenient().when(mailTemplateService.findActiveByCode(anyString())).thenReturn(Optional.empty());
    }

    // ── Reply target derivation from source event ─────────────────────────────

    @Test
    void sendReply_derivesToAddressFromSourceEvent_fromHeader() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);

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

        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);

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

        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), eq(10L), eq(42L),
                eq("support@caseflow.dev"),
                eq("john@bigcorp.com"),
                eq("john@bigcorp.com"),
                anyString(), any(), any(), any(), any());
    }

    @Test
    void sendReply_usesToAddressOverride_whenNoSourceEventId() {
        sendReply(1L, 2L, null, "manual@example.com", "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L, null, null);

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
                sendReply(1L, 2L, null, null, "support@caseflow.dev",
                        "Re: Issue", "body", null, null, 42L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceEventId or toAddress");
    }

    @Test
    void sendReply_throws_whenSourceEventNotFound() {
        when(ingressEventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                sendReply(1L, 2L, 999L, null, "support@caseflow.dev",
                        "Re: Issue", "body", null, null, 42L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ingress event not found");
    }

    // ── No premature system transition ────────────────────────────────────────

    @Test
    void sendReply_doesNotApplySystemTransition_atEnqueueTime() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        // Must complete without TicketSystemTransitionService (not in constructor)
        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);
    }

    // ── Returns dispatch info ─────────────────────────────────────────────────

    @Test
    void sendReply_returnsDispatch_withResolvedAddress() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        OutboundEmailDispatch result = sendReply(1L, 2L, 10L, null, "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(99L);
    }

    // ── History recording ─────────────────────────────────────────────────────

    @Test
    void sendReply_recordsOutboundReplyQueuedEvent() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);

        verify(historyService).recordOutboundReplyQueued(
                eq(1L), any(), eq(99L), eq("customer@example.com"), eq(42L));
    }

    // ── RFC 2822 threading header derivation ──────────────────────────────────

    @Test
    void sendReply_derivesInReplyTo_fromSourceEventMessageId_whenNotExplicit() {
        sourceEvent.setRawReplyTo(null);
        when(ingressEventRepository.findById(10L)).thenReturn(Optional.of(sourceEvent));

        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);

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

        sendReply(1L, 2L, 10L, null, "support@caseflow.dev", "Re: Issue", "body", null, null, 42L, null, null);

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
        sendReply(1L, 2L, null, "manual@example.com", "support@caseflow.dev",
                "Re: Issue", "body", null, null, 42L, null, null);

        verify(dispatchService).enqueue(
                eq(1L), eq(2L), isNull(), eq(42L),
                eq("support@caseflow.dev"),
                eq("manual@example.com"),
                eq("manual@example.com"),
                anyString(), any(), any(),
                isNull(),
                isNull());
    }

    // ── Template rendering — HTML body handling ───────────────────────────────

    @Test
    void sendReply_usesHtmlBody_forHtmlTemplate_whenHtmlBodyProvided() {
        MailTemplate tpl = makeTemplate("CUSTOMER_REPLY", "<html>{replyBody}</html>", "{replyBody}");
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(tpl));
        when(mailTemplateService.substitute(eq("<html>{replyBody}</html>"), anyString(), any(), any(), any(), any()))
                .thenReturn("<html>rendered html</html>");
        when(mailTemplateService.substitute(eq("{replyBody}"), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered text");

        sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "plain text", "<b>html content</b>", null, 1L, null, null);

        verify(mailTemplateService, never()).escapeHtml(anyString());
    }

    @Test
    void sendReply_escapesTextBody_forHtmlTemplate_whenHtmlBodyAbsent() {
        MailTemplate tpl = makeTemplate("CUSTOMER_REPLY", "<html>{replyBody}</html>", "{replyBody}");
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(tpl));
        when(mailTemplateService.escapeHtml("plain text with <tags>")).thenReturn("plain text with &lt;tags&gt;");
        when(mailTemplateService.substitute(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered");

        sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "plain text with <tags>", null, null, 1L, null, null);

        verify(mailTemplateService).escapeHtml("plain text with <tags>");
    }

    // ── Template selection ────────────────────────────────────────────────────

    @Test
    void sendReply_selectsTemplateByCode_whenTemplateCodeProvided() {
        MailTemplate tpl = makeTemplate("CUSTOM_REPLY", "<p>{replyBody}</p>", "{replyBody}");
        when(mailTemplateService.findActiveByCode("CUSTOM_REPLY")).thenReturn(Optional.of(tpl));
        when(mailTemplateService.substitute(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered");

        sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "body", null, null, 1L, null, "CUSTOM_REPLY");

        verify(mailTemplateService).findActiveByCode("CUSTOM_REPLY");
    }

    @Test
    void sendReply_selectsTemplateById_whenTemplateIdProvided() {
        MailTemplate tpl = makeTemplate("SPECIAL", "<p>{replyBody}</p>", "{replyBody}");
        tpl.setIsActive(true);
        when(mailTemplateService.findById(7L)).thenReturn(tpl);
        when(mailTemplateService.substitute(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered");

        sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "body", null, null, 1L, 7L, null);

        verify(mailTemplateService).findById(7L);
        // Should NOT fall back to findActiveByCode if templateId lookup succeeded
        verify(mailTemplateService, never()).findActiveByCode("CUSTOMER_REPLY");
    }

    @Test
    void sendReply_recordsTemplateUsed_whenTemplateApplied() {
        MailTemplate tpl = makeTemplate("CUSTOMER_REPLY", "<p>{replyBody}</p>", "{replyBody}");
        tpl.setIsActive(true);
        try {
            var f = MailTemplate.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(tpl, 3L);
        } catch (Exception e) { throw new RuntimeException(e); }
        when(mailTemplateService.findById(3L)).thenReturn(tpl);
        when(mailTemplateService.substitute(anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("rendered");

        sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "body", null, null, 1L, 3L, null);

        verify(historyService).recordTemplateUsed(eq(1L), eq(3L), eq("CUSTOMER_REPLY"), eq(1L));
    }

    @Test
    void sendReply_doesNotRecordTemplateUsed_whenNoTemplateFound() {
        when(mailTemplateService.findActiveByCode(anyString())).thenReturn(Optional.empty());

        sendReply(1L, 2L, null, "to@example.com", "from@example.com",
                "Subject", "body", null, null, 1L, null, null);

        verify(historyService, never()).recordTemplateUsed(anyLong(), anyLong(), anyString(), anyLong());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private OutboundEmailDispatch sendReply(Long ticketId, Long mailboxId, Long sourceEventId,
                                             String toAddress, String fromAddress,
                                             String subject, String textBody, String htmlBody,
                                             String inReplyToMessageId, Long sentByUserId,
                                             Long templateId, String templateCode) {
        return sut.sendReply(ticketId, mailboxId, sourceEventId, toAddress, fromAddress,
                subject, textBody, htmlBody, inReplyToMessageId, sentByUserId,
                templateId, templateCode);
    }

    private static MailTemplate makeTemplate(String code, String html, String text) {
        MailTemplate tpl = new MailTemplate();
        tpl.setCode(code);
        tpl.setHtmlTemplate(html);
        tpl.setPlainTextTemplate(text);
        tpl.setIsActive(true);
        return tpl;
    }
}
