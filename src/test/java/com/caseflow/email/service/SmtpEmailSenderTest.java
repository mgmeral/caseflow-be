package com.caseflow.email.service;

import com.caseflow.email.api.dto.SmtpConnectionTestResponse;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.ServerSocket;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender(Optional.empty());
    }

    // ── Multipart-mode: MimeMessageHelper must use multipart=true when HTML present ──

    @Test
    void send_doesNotThrow_withHtmlAndTextBodies() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new org.springframework.mail.javamail.JavaMailSenderImpl()
                .createMimeMessage();
        when(mockMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailSender senderWithGlobal = new SmtpEmailSender(Optional.of(mockMailSender));

        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setFromAddress("from@example.com");
        dispatch.setToAddress("to@example.com");
        dispatch.setSubject("Test");
        dispatch.setTextBody("Plain text body");
        dispatch.setHtmlBody("<p>HTML body</p>");

        // Must NOT throw IllegalStateException: "Not in multipart mode"
        senderWithGlobal.send(dispatch);
    }

    @Test
    void send_doesNotThrow_withTextBodyOnly() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new org.springframework.mail.javamail.JavaMailSenderImpl()
                .createMimeMessage();
        when(mockMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailSender senderWithGlobal = new SmtpEmailSender(Optional.of(mockMailSender));

        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setFromAddress("from@example.com");
        dispatch.setToAddress("to@example.com");
        dispatch.setSubject("Test");
        dispatch.setTextBody("Plain text only");
        dispatch.setHtmlBody(null);

        // Must NOT throw
        senderWithGlobal.send(dispatch);
    }

    @Test
    void send_setsReferencesHeader_whenBothInReplyToAndReferencesPresent() throws Exception {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new org.springframework.mail.javamail.JavaMailSenderImpl()
                .createMimeMessage();
        when(mockMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailSender senderWithGlobal = new SmtpEmailSender(Optional.of(mockMailSender));

        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setFromAddress("from@example.com");
        dispatch.setToAddress("to@example.com");
        dispatch.setSubject("Re: Test");
        dispatch.setTextBody("Reply text");
        dispatch.setInReplyToMessageId("<parent@example.com>");
        dispatch.setReferencesHeader("<root@example.com> <parent@example.com>");

        senderWithGlobal.send(dispatch);

        assertThat(mimeMessage.getHeader("In-Reply-To")).contains("<parent@example.com>");
        assertThat(mimeMessage.getHeader("References")).contains("<root@example.com> <parent@example.com>");
    }

    @Test
    void send_fallsBackToInReplyTo_forReferences_whenReferencesHeaderNull() throws Exception {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new org.springframework.mail.javamail.JavaMailSenderImpl()
                .createMimeMessage();
        when(mockMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailSender senderWithGlobal = new SmtpEmailSender(Optional.of(mockMailSender));

        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setFromAddress("from@example.com");
        dispatch.setToAddress("to@example.com");
        dispatch.setSubject("Re: Test");
        dispatch.setTextBody("Reply text");
        dispatch.setInReplyToMessageId("<parent@example.com>");
        dispatch.setReferencesHeader(null); // no chain — should fall back to In-Reply-To

        senderWithGlobal.send(dispatch);

        assertThat(mimeMessage.getHeader("References")).contains("<parent@example.com>");
    }

    @Test
    void send_doesNotThrow_withHtmlBodyOnly_noTextBody() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new org.springframework.mail.javamail.JavaMailSenderImpl()
                .createMimeMessage();
        when(mockMailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpEmailSender senderWithGlobal = new SmtpEmailSender(Optional.of(mockMailSender));

        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setFromAddress("from@example.com");
        dispatch.setToAddress("to@example.com");
        dispatch.setSubject("HTML-only");
        dispatch.setTextBody(null);
        dispatch.setHtmlBody("<p>HTML only — no plain text</p>");

        // Must use multipart mode because hasHtml=true; must NOT throw
        senderWithGlobal.send(dispatch);
    }

    // ── Missing config → failure ──────────────────────────────────────────────

    @Test
    void testSmtpConnection_returnsFailure_whenSmtpHostNotConfigured() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost(null);
        mailbox.setSmtpPort(587);

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("SMTP host is not configured");
        assertThat(result.testedAt()).isNotNull();
    }

    @Test
    void testSmtpConnection_returnsFailure_whenSmtpHostIsBlank() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("   ");
        mailbox.setSmtpPort(587);

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("SMTP host is not configured");
    }

    @Test
    void testSmtpConnection_returnsFailure_whenSmtpPortNotConfigured() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("smtp.example.com");
        mailbox.setSmtpPort(null);

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("SMTP port is not configured");
    }

    // ── IMAP-for-SMTP misconfig detection ─────────────────────────────────────

    @Test
    void testSmtpConnection_detectsMisconfig_whenSmtpPortIsImapPort993() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("mail.example.com");
        mailbox.setSmtpPort(993);
        mailbox.setImapHost("mail.example.com");

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("misconfig");
        assertThat(result.message()).contains("993");
    }

    @Test
    void testSmtpConnection_detectsMisconfig_whenSmtpPortIsImapPort143() {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("mail.example.com");
        mailbox.setSmtpPort(143);
        mailbox.setImapHost("mail.example.com");

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("misconfig");
        assertThat(result.message()).contains("143");
    }

    @Test
    void testSmtpConnection_doesNotFlagMisconfig_whenHostsDiffer() {
        // Same IMAP port but on a different host — not a misconfig
        // (Will attempt TCP and fail; we just verify no misconfig message)
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("localhost");
        mailbox.setSmtpPort(port);
        mailbox.setImapHost("imap.example.com"); // different host

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.message()).doesNotContain("misconfig");
    }

    @Test
    void testSmtpConnection_doesNotFlagMisconfig_whenSmtpPortIsNormalSmtpPort() {
        // Port 587 with same host — not a misconfig even if host matches
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("localhost");
        mailbox.setSmtpPort(587);
        mailbox.setImapHost("localhost"); // same host, but 587 is not an IMAP port

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.message()).doesNotContain("misconfig");
    }

    // ── TCP connection failure ────────────────────────────────────────────────

    @Test
    void testSmtpConnection_returnsFailure_whenConnectionRefused() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("localhost");
        mailbox.setSmtpPort(port);
        mailbox.setImapHost("imap.example.com");

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("cannot connect");
        assertThat(result.testedAt()).isNotNull();
    }

    @Test
    void testSmtpConnection_doesNotExposePassword_inErrorMessage() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("localhost");
        mailbox.setSmtpPort(port);
        mailbox.setSmtpPassword("super-secret-smtp-password");
        mailbox.setImapHost("imap.example.com");

        SmtpConnectionTestResponse result = sender.testSmtpConnection(mailbox);

        assertThat(result.message()).doesNotContain("super-secret-smtp-password");
    }
}
