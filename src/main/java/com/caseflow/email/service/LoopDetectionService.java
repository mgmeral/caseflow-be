package com.caseflow.email.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects auto-replies, bounces, and mail loops that should not create tickets.
 */
@Service
public class LoopDetectionService {

    private static final Pattern AUTO_REPLY_SUBJECT = Pattern.compile(
            "(?i)^(out of office|automatic reply|auto-reply|autoreply|vacation|away from|"
                    + "delivery status notification|undeliverable|mail delivery failed|"
                    + "delivery failure|failure notice|mailer-daemon).*"
    );

    private static final List<String> LOOP_INDICATORS = List.of(
            "x-autorespond",
            "x-auto-response-suppress",
            "auto-submitted",
            "x-mailer-daemon"
    );

    /**
     * Returns true if the email appears to be an auto-reply, bounce, or loop message
     * and should not be further processed.
     */
    public boolean isLoop(String subject, String fromAddress, List<String> rawHeaders) {
        if (isAutoReplySubject(subject)) {
            return true;
        }
        if (isMailerDaemonAddress(fromAddress)) {
            return true;
        }
        if (containsLoopHeader(rawHeaders)) {
            return true;
        }
        return false;
    }

    private boolean isAutoReplySubject(String subject) {
        if (subject == null || subject.isBlank()) return false;
        return AUTO_REPLY_SUBJECT.matcher(subject.trim()).matches();
    }

    private boolean isMailerDaemonAddress(String from) {
        if (from == null) return false;
        String lower = from.toLowerCase();
        return lower.contains("mailer-daemon") || lower.contains("postmaster@")
                || lower.contains("noreply@") || lower.contains("no-reply@");
    }

    private boolean containsLoopHeader(List<String> headers) {
        if (headers == null || headers.isEmpty()) return false;
        for (String header : headers) {
            String lower = header.toLowerCase();
            for (String indicator : LOOP_INDICATORS) {
                if (lower.startsWith(indicator)) {
                    return true;
                }
            }
        }
        return false;
    }
}
