package com.caseflow.email.service;

import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.email.api.dto.SmtpConnectionTestResponse;
import com.caseflow.email.domain.DispatchFailureCategory;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import jakarta.mail.MessagingException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Set;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;

/**
 * Sends outbound emails via SMTP.
 *
 * <h2>Sender selection</h2>
 * <ol>
 *   <li>If the dispatch has a {@code mailboxId} and the mailbox has SMTP host configured,
 *       a per-mailbox {@link JavaMailSenderImpl} is built from the mailbox's SMTP settings.</li>
 *   <li>Otherwise falls back to the optional global {@link JavaMailSender} bean.</li>
 *   <li>If neither is available, throws {@link EmailDispatchException} with category
 *       {@link DispatchFailureCategory#UNCONFIGURED} — callers must mark the dispatch
 *       PERMANENTLY_FAILED rather than retrying.</li>
 * </ol>
 *
 * <p>Mailbox-specific SMTP is the production path. The global sender is an explicit fallback
 * for environments where all mailboxes share one relay (e.g. dev/test).
 */
@Service
public class SmtpEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final Optional<JavaMailSender> globalMailSender;

    public SmtpEmailSender(Optional<JavaMailSender> globalMailSender) {
        this.globalMailSender = globalMailSender;
        if (globalMailSender.isEmpty()) {
            log.info("SmtpEmailSender: no global JavaMailSender configured — will use per-mailbox SMTP settings");
        }
    }

    /**
     * Sends the dispatch using mailbox-specific SMTP settings when available,
     * falling back to the global sender.
     *
     * @param dispatch the outbound dispatch record
     * @param mailbox  the mailbox to send from (may be null for global-sender fallback)
     * @throws EmailDispatchException with categorised failure if sending fails
     */
    public void send(OutboundEmailDispatch dispatch, EmailMailbox mailbox) {
        JavaMailSender sender = resolveSender(mailbox, dispatch.getId());
        doSend(sender, dispatch);
    }

    /**
     * Sends using the global sender only (legacy path — prefer {@link #send(OutboundEmailDispatch, EmailMailbox)}).
     *
     * @throws EmailDispatchException with category {@link DispatchFailureCategory#UNCONFIGURED}
     *         if the global sender is not configured.
     */
    public void send(OutboundEmailDispatch dispatch) {
        send(dispatch, null);
    }

    public boolean isConfigured() {
        return globalMailSender.isPresent();
    }

    /**
     * Validates SMTP configuration and tests TCP reachability of the SMTP endpoint.
     *
     * <p>Also detects an obvious misconfiguration: SMTP host/port identical to common IMAP
     * ports (143, 993) on the same host — which typically indicates the IMAP settings were
     * accidentally reused for the SMTP field.
     *
     * <p>No credentials are submitted — this is a connectivity + config sanity check only.
     */
    public SmtpConnectionTestResponse testSmtpConnection(EmailMailbox mailbox) {
        if (mailbox.getSmtpHost() == null || mailbox.getSmtpHost().isBlank()) {
            return new SmtpConnectionTestResponse(false,
                    "SMTP_TEST_CONNECTION failed: SMTP host is not configured", Instant.now());
        }
        if (mailbox.getSmtpPort() == null) {
            return new SmtpConnectionTestResponse(false,
                    "SMTP_TEST_CONNECTION failed: SMTP port is not configured", Instant.now());
        }

        // Detect obvious IMAP-for-SMTP misconfig
        Set<Integer> imapPorts = Set.of(143, 993);
        if (mailbox.getImapHost() != null
                && mailbox.getSmtpHost().equalsIgnoreCase(mailbox.getImapHost())
                && imapPorts.contains(mailbox.getSmtpPort())) {
            log.warn("SMTP_TEST_CONNECTION misconfig — mailbox {}: smtpHost='{}', smtpPort={} matches IMAP settings",
                    mailbox.getId(), mailbox.getSmtpHost(), mailbox.getSmtpPort());
            return new SmtpConnectionTestResponse(false,
                    "SMTP_TEST_CONNECTION misconfig: SMTP port " + mailbox.getSmtpPort()
                            + " on host '" + mailbox.getSmtpHost()
                            + "' is a common IMAP port (143/993) — SMTP settings appear to be misconfigured",
                    Instant.now());
        }

        // TCP reachability check
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(mailbox.getSmtpHost(), mailbox.getSmtpPort()), 5000);
            log.info("SMTP_TEST_CONNECTION success — mailbox {}, host: {}:{}",
                    mailbox.getId(), mailbox.getSmtpHost(), mailbox.getSmtpPort());
            return new SmtpConnectionTestResponse(true,
                    "SMTP_TEST_CONNECTION succeeded — TCP connection to "
                            + mailbox.getSmtpHost() + ":" + mailbox.getSmtpPort() + " established",
                    Instant.now());
        } catch (Exception e) {
            log.warn("SMTP_TEST_CONNECTION failed — mailbox {}: {}", mailbox.getId(), e.getMessage());
            return new SmtpConnectionTestResponse(false,
                    "SMTP_TEST_CONNECTION failed — cannot connect to "
                            + mailbox.getSmtpHost() + ":" + mailbox.getSmtpPort()
                            + ": " + sanitizeSmtpError(e.getMessage()),
                    Instant.now());
        }
    }

    private static String sanitizeSmtpError(String message) {
        if (message == null) return "connection refused";
        return message.replaceAll("(?i)password=\\S+", "password=[REDACTED]");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private JavaMailSender resolveSender(EmailMailbox mailbox, Long dispatchId) {
        if (mailbox != null && mailbox.getSmtpHost() != null && !mailbox.getSmtpHost().isBlank()) {
            log.debug("SMTP_SEND using mailbox SMTP config — mailboxId: {}, host: {}, dispatchId: {}",
                    mailbox.getId(), mailbox.getSmtpHost(), dispatchId);
            return buildMailboxSender(mailbox);
        }
        if (globalMailSender.isPresent()) {
            log.debug("SMTP_SEND using global JavaMailSender — dispatchId: {}", dispatchId);
            return globalMailSender.get();
        }
        throw new EmailDispatchException(
                "SMTP not configured: mailbox has no SMTP settings and no global sender is available",
                DispatchFailureCategory.UNCONFIGURED);
    }

    private JavaMailSenderImpl buildMailboxSender(EmailMailbox mailbox) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailbox.getSmtpHost());
        if (mailbox.getSmtpPort() != null) {
            sender.setPort(mailbox.getSmtpPort());
        }
        if (mailbox.getSmtpUsername() != null) {
            sender.setUsername(mailbox.getSmtpUsername());
        }
        if (mailbox.getSmtpPassword() != null) {
            sender.setPassword(mailbox.getSmtpPassword());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", mailbox.getSmtpUsername() != null ? "true" : "false");

        if (Boolean.TRUE.equals(mailbox.getSmtpUseSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        return sender;
    }

    private void doSend(JavaMailSender sender, OutboundEmailDispatch dispatch) {
        try {
            boolean hasHtml = dispatch.getHtmlBody() != null && !dispatch.getHtmlBody().isBlank();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, hasHtml, "UTF-8");
            helper.setFrom(dispatch.getFromAddress());
            helper.setTo(dispatch.getToAddress());
            helper.setSubject(dispatch.getSubject());

            if (dispatch.getInReplyToMessageId() != null) {
                message.setHeader("In-Reply-To", dispatch.getInReplyToMessageId());
                // Prefer the full references chain; fall back to single In-Reply-To value
                String refs = dispatch.getReferencesHeader() != null
                        ? dispatch.getReferencesHeader()
                        : dispatch.getInReplyToMessageId();
                message.setHeader("References", refs);
            }

            if (hasHtml) {
                helper.setText(
                        dispatch.getTextBody() != null ? dispatch.getTextBody() : "",
                        dispatch.getHtmlBody()
                );
            } else {
                helper.setText(dispatch.getTextBody() != null ? dispatch.getTextBody() : "");
            }

            sender.send(message);
            log.info("SMTP_SEND sent — dispatchId: {}, to: '{}', subject: '{}'",
                    dispatch.getId(), dispatch.getToAddress(), dispatch.getSubject());

        } catch (MessagingException e) {
            DispatchFailureCategory category = categorise(e);
            throw new EmailDispatchException(
                    "Failed to send email for dispatchId " + dispatch.getId() + ": " + e.getMessage(),
                    category,
                    e);
        }
    }

    private DispatchFailureCategory categorise(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("authentication") || msg.contains("credentials") || msg.contains("535")) {
            return DispatchFailureCategory.SMTP_AUTH_FAILURE;
        }
        if (msg.contains("ssl") || msg.contains("tls") || msg.contains("handshake")
                || msg.contains("connection refused") || msg.contains("connect")) {
            return DispatchFailureCategory.TLS_FAILURE;
        }
        if (msg.contains("invalid address") || msg.contains("bad address")
                || msg.contains("550") || msg.contains("553")) {
            return DispatchFailureCategory.INVALID_ADDRESS;
        }
        return DispatchFailureCategory.UNKNOWN;
    }
}
