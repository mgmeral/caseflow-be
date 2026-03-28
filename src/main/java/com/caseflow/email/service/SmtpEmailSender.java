package com.caseflow.email.service;

import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.email.domain.OutboundEmailDispatch;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Sends outbound emails via SMTP using an optionally-configured {@link JavaMailSender}.
 *
 * <p>If SMTP is not configured (sender is absent), {@link #send} throws
 * {@link EmailDispatchException} permanently — callers must mark the dispatch
 * as PERMANENTLY_FAILED rather than retrying.
 */
@Service
public class SmtpEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final Optional<JavaMailSender> mailSender;

    public SmtpEmailSender(Optional<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
        if (mailSender.isEmpty()) {
            log.warn("SmtpEmailSender: no JavaMailSender configured — outbound email is disabled");
        }
    }

    /**
     * Sends the dispatch via SMTP.
     *
     * @throws EmailDispatchException if SMTP is unconfigured (permanent) or a send error occurs (transient)
     */
    public void send(OutboundEmailDispatch dispatch) {
        if (mailSender.isEmpty()) {
            throw new EmailDispatchException("SMTP not configured — outbound email is disabled");
        }

        JavaMailSender sender = mailSender.get();
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(dispatch.getFromAddress());
            helper.setTo(dispatch.getToAddress());
            helper.setSubject(dispatch.getSubject());

            if (dispatch.getInReplyToMessageId() != null) {
                message.setHeader("In-Reply-To", dispatch.getInReplyToMessageId());
                message.setHeader("References", dispatch.getInReplyToMessageId());
            }

            if (dispatch.getHtmlBody() != null && !dispatch.getHtmlBody().isBlank()) {
                helper.setText(
                        dispatch.getTextBody() != null ? dispatch.getTextBody() : "",
                        dispatch.getHtmlBody()
                );
            } else {
                helper.setText(dispatch.getTextBody() != null ? dispatch.getTextBody() : "");
            }

            sender.send(message);
            log.info("Email sent — dispatchId: {}, to: '{}', subject: '{}'",
                    dispatch.getId(), dispatch.getToAddress(), dispatch.getSubject());

        } catch (MessagingException e) {
            throw new EmailDispatchException("Failed to send email for dispatchId " + dispatch.getId(), e);
        }
    }

    public boolean isConfigured() {
        return mailSender.isPresent();
    }
}
