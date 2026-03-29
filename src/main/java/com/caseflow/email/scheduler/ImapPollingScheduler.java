package com.caseflow.email.scheduler;

import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.ImapMailboxPoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled runner for IMAP mailbox polling MVP.
 *
 * <p>Runs every {@code caseflow.email.imap.scheduler-interval-ms} milliseconds (default: 30s).
 * For each polling-enabled mailbox, checks whether enough time has elapsed since the last poll
 * ({@code pollIntervalSeconds}) before invoking the poller.
 *
 * <p>This coarse scheduling keeps the implementation simple while respecting per-mailbox
 * poll intervals without requiring a separate thread per mailbox.
 */
@Component
public class ImapPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImapPollingScheduler.class);

    private final EmailMailboxService mailboxService;
    private final ImapMailboxPoller poller;

    public ImapPollingScheduler(EmailMailboxService mailboxService, ImapMailboxPoller poller) {
        this.mailboxService = mailboxService;
        this.poller = poller;
    }

    @Scheduled(fixedDelayString = "${caseflow.email.imap.scheduler-interval-ms:30000}")
    public void run() {
        List<EmailMailbox> mailboxes = mailboxService.findPollingEnabled();
        if (mailboxes.isEmpty()) return;

        log.debug("IMAP polling tick — checking {} polling-enabled mailbox(es)", mailboxes.size());

        Instant now = Instant.now();
        for (EmailMailbox mailbox : mailboxes) {
            if (isDue(mailbox, now)) {
                try {
                    poller.pollMailbox(mailbox);
                } catch (Exception e) {
                    log.error("Unhandled error polling mailbox {} — {}", mailbox.getId(), e.getMessage(), e);
                }
            }
        }
    }

    private boolean isDue(EmailMailbox mailbox, Instant now) {
        if (mailbox.getLastPollAt() == null) return true;
        int intervalSeconds = mailbox.getPollIntervalSeconds() != null ? mailbox.getPollIntervalSeconds() : 60;
        return mailbox.getLastPollAt().plusSeconds(intervalSeconds).isBefore(now);
    }
}
