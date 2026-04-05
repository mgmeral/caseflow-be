package com.caseflow.email.service;

import com.caseflow.email.api.dto.ReplyPreviewRequest;
import com.caseflow.email.api.dto.ReplyPreviewResponse;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyPreviewServiceTest {

    @Mock EmailIngressEventRepository ingressEventRepository;
    @Mock EmailMailboxService mailboxService;
    @Mock MailTemplateService mailTemplateService;

    @InjectMocks ReplyPreviewService previewService;

    @Test
    void preview_derivesReplyToFromSourceEvent_replyToHeader() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);
        EmailIngressEvent event = event(100L, 1L, "customer@example.com", "reply-to@example.com");
        MailTemplate template = template();

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(ingressEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(template));
        when(mailTemplateService.substitute(anyString(), anyString(), anyString(),
                anyString(), any(), any())).thenReturn("rendered");
        when(mailTemplateService.escapeHtml(any())).thenReturn("");

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, 100L, null, null, null, "Hello", null);
        ReplyPreviewResponse response = previewService.preview(ticket, request, "Agent Smith");

        // Reply-To header takes precedence over From
        assertThat(response.derivedToAddress()).isEqualTo("reply-to@example.com");
    }

    @Test
    void preview_fallsBackToFromWhenNoReplyToHeader() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);
        EmailIngressEvent event = event(100L, 1L, "customer@example.com", null);
        MailTemplate template = template();

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(ingressEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(template));
        when(mailTemplateService.substitute(anyString(), anyString(), anyString(),
                anyString(), any(), any())).thenReturn("rendered");
        when(mailTemplateService.escapeHtml(any())).thenReturn("");

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, 100L, null, null, null, "Hello", null);
        ReplyPreviewResponse response = previewService.preview(ticket, request, "Agent");

        assertThat(response.derivedToAddress()).isEqualTo("customer@example.com");
    }

    @Test
    void preview_rejectsInactiveMailbox() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox inactiveMailbox = mailbox(10L, "support@example.com", false);
        when(mailboxService.getById(10L)).thenReturn(inactiveMailbox);

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, null, null, null, null, "Hello", null);

        assertThatThrownBy(() -> previewService.preview(ticket, request, "Agent"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void preview_rejectsSourceEventFromDifferentTicket() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);
        // Event belongs to ticket 99, not 1
        EmailIngressEvent event = event(100L, 99L, "customer@example.com", null);

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(ingressEventRepository.findById(100L)).thenReturn(Optional.of(event));

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, 100L, null, null, null, "Hello", null);

        assertThatThrownBy(() -> previewService.preview(ticket, request, "Agent"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void preview_isEditableAlways() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);
        MailTemplate template = template();

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(template));
        when(mailTemplateService.substitute(anyString(), anyString(), anyString(),
                anyString(), any(), any())).thenReturn("rendered");
        when(mailTemplateService.escapeHtml(any())).thenReturn("");

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, null, null, null, null, "Hi", null);
        ReplyPreviewResponse response = previewService.preview(ticket, request, "Agent");

        assertThat(response.isEditable()).isTrue();
    }

    @Test
    void preview_detectsUnknownPlaceholderInRenderedOutput() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);
        MailTemplate template = template();

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(template));
        // Substitute leaves an unrecognized placeholder in the rendered output
        when(mailTemplateService.substitute(anyString(), anyString(), anyString(),
                anyString(), any(), any())).thenReturn("Hello {custom_var}");
        when(mailTemplateService.escapeHtml(any())).thenReturn("Hello {custom_var}");

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, null, null, null, null, "Hello", null);
        ReplyPreviewResponse response = previewService.preview(ticket, request, "Agent");

        // {custom_var} is not a known placeholder — must appear as UNKNOWN diagnostic
        assertThat(response.placeholderDiagnostics())
                .anyMatch(d -> d.placeholder().equals("{custom_var}") && d.severity().equals("UNKNOWN"));
    }

    @Test
    void preview_usesBuiltInFallback_whenNoTemplateFound() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.empty());

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, null, null, null, null, "My message", null);
        ReplyPreviewResponse response = previewService.preview(ticket, request, "Agent");

        // Fallback appends the ticket reference
        assertThat(response.bodyText()).contains("My message");
        assertThat(response.bodyText()).contains("TKT-001");
        assertThat(response.bodyHtml()).isNull();
        // A warning is emitted to inform the agent
        assertThat(response.warnings()).anyMatch(w -> w.toLowerCase().contains("fallback"));
        assertThat(response.templateInfo()).isNull();
    }

    @Test
    void preview_usesSubjectOverride() {
        Ticket ticket = ticket(1L, "TKT-001");
        EmailMailbox mailbox = mailbox(10L, "support@example.com", true);
        MailTemplate template = template();

        when(mailboxService.getById(10L)).thenReturn(mailbox);
        when(mailTemplateService.findActiveByCode("CUSTOMER_REPLY")).thenReturn(Optional.of(template));
        when(mailTemplateService.substitute(anyString(), anyString(), anyString(),
                anyString(), any(), any())).thenReturn("rendered");
        when(mailTemplateService.escapeHtml(any())).thenReturn("");

        ReplyPreviewRequest request = new ReplyPreviewRequest(10L, null, null, null,
                "Custom Subject", "body", null);
        ReplyPreviewResponse response = previewService.preview(ticket, request, "Agent");

        assertThat(response.subject()).isEqualTo("Custom Subject");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticket ticket(Long id, String ticketNo) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setSubject("Test ticket");
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setPriority(TicketPriority.MEDIUM);
        try {
            var f = Ticket.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
            var pf = Ticket.class.getDeclaredField("publicId");
            pf.setAccessible(true);
            pf.set(t, UUID.randomUUID());
            var ca = Ticket.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(t, Instant.now());
            var ua = Ticket.class.getDeclaredField("updatedAt");
            ua.setAccessible(true);
            ua.set(t, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private EmailMailbox mailbox(Long id, String address, boolean active) {
        EmailMailbox mb = new EmailMailbox();
        mb.setName("Support");
        mb.setAddress(address);
        mb.setIsActive(active);
        try {
            var f = EmailMailbox.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(mb, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return mb;
    }

    private EmailIngressEvent event(Long id, Long ticketId, String rawFrom, String rawReplyTo) {
        EmailIngressEvent e = new EmailIngressEvent();
        e.setRawFrom(rawFrom);
        e.setRawReplyTo(rawReplyTo);
        e.setTicketId(ticketId);
        e.setMessageId("<msg-" + id + "@test>");
        try {
            var f = EmailIngressEvent.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    private MailTemplate template() {
        MailTemplate t = new MailTemplate();
        t.setCode("CUSTOMER_REPLY");
        t.setName("Customer Reply");
        t.setSubjectTemplate("Re: {ticketRef}");
        t.setPlainTextTemplate("{replyBody}\n\nTicket: {ticketRef}");
        t.setHtmlTemplate("<p>{replyBody}</p>");
        t.setIsActive(true);
        t.setIsBuiltIn(true);
        try {
            var f = MailTemplate.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, 1L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }
}
