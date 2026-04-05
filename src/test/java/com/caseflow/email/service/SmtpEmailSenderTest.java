package com.caseflow.email.service;

import com.caseflow.email.api.dto.SmtpConnectionTestResponse;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
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

    // ── Transport mode: 465 implicit SSL ────────────────────────────────────

    @Test
    void buildMailboxSender_port465_setsImplicitSslOnly() throws Exception {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("smtp.gmail.com");
        mailbox.setSmtpPort(465);
        mailbox.setSmtpUsername("user@example.com");
        mailbox.setSmtpPassword("pass");
        mailbox.setSmtpUseSsl(true);
        mailbox.setSmtpStarttls(false);

        Properties props = buildPropertiesFor(mailbox);

        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isNull();
        assertThat(props.getProperty("mail.smtp.starttls.required")).isNull();
    }

    @Test
    void buildMailboxSender_port587_setsStarttlsOnly() throws Exception {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("smtp.gmail.com");
        mailbox.setSmtpPort(587);
        mailbox.setSmtpUsername("user@example.com");
        mailbox.setSmtpPassword("pass");
        mailbox.setSmtpUseSsl(false);
        mailbox.setSmtpStarttls(true);

        Properties props = buildPropertiesFor(mailbox);

        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.ssl.enable")).isNull();
    }

    @Test
    void buildMailboxSender_port587_doesNotSetImplicitSsl() throws Exception {
        // This test guards against the SSLException regression.
        // If mail.smtp.ssl.enable were set to "true" on a STARTTLS port (587),
        // the sender would attempt a TLS handshake immediately, causing:
        // javax.net.ssl.SSLException: Unsupported or unrecognized SSL message
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("smtp.gmail.com");
        mailbox.setSmtpPort(587);
        mailbox.setSmtpUseSsl(false);
        mailbox.setSmtpStarttls(true);

        Properties props = buildPropertiesFor(mailbox);

        assertThat(props.getProperty("mail.smtp.ssl.enable"))
                .as("ssl.enable must NOT be set for STARTTLS (port 587)")
                .isNull();
    }

    @Test
    void buildMailboxSender_bothSslAndStarttls_sslWins_andNoStarttls() throws Exception {
        // Contradictory config: ssl takes precedence per the guard in buildMailboxSender.
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("smtp.example.com");
        mailbox.setSmtpPort(465);
        mailbox.setSmtpUseSsl(true);
        mailbox.setSmtpStarttls(true); // contradictory

        Properties props = buildPropertiesFor(mailbox);

        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isNull();
    }

    @Test
    void buildMailboxSender_neitherSslNorStarttls_noTlsProperties() throws Exception {
        EmailMailbox mailbox = new EmailMailbox();
        mailbox.setSmtpHost("relay.internal");
        mailbox.setSmtpPort(25);
        mailbox.setSmtpUseSsl(false);
        mailbox.setSmtpStarttls(false);

        Properties props = buildPropertiesFor(mailbox);

        assertThat(props.getProperty("mail.smtp.ssl.enable")).isNull();
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isNull();
    }

    // ── Helper: invoke package-private buildMailboxSender via reflection ──────

    /**
     * Uses reflection to call the private {@code buildMailboxSender} method and
     * extract the resulting JavaMailProperties so we can assert on TLS configuration
     * without actually opening a network socket.
     */
    private static Properties buildPropertiesFor(EmailMailbox mailbox) throws Exception {
        SmtpEmailSender s = new SmtpEmailSender(Optional.empty());
        Method method = SmtpEmailSender.class.getDeclaredMethod("buildMailboxSender", EmailMailbox.class);
        method.setAccessible(true);
        JavaMailSenderImpl built = (JavaMailSenderImpl) method.invoke(s, mailbox);
        return built.getJavaMailProperties();
    }
}
