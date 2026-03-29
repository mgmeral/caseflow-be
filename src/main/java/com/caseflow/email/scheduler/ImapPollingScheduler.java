package com.caseflow.email.scheduler;

import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.ImapMailboxPoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled runner for IMAP mailbox polling.
 *
 * <h2>Per-mailbox interval</h2>
 * Runs every {@code caseflow.email.imap.scheduler-interval-ms} ms (default 30s).
 * For each mailbox, checks whether {@code pollIntervalSeconds} has elapsed before polling.
 *
 * <h2>Multi-instance safety</h2>
 * Before polling a mailbox, acquires a DB-level lease via {@code tryClaimForPolling}.
 * Only one instance may hold the lease at a time.  The lease expires after
 * {@code caseflow.email.imap.poll-lease-ttl-seconds} (default 600s / 10min) so that a crashed
 * pod does not permanently block polling.  The lease is always released by
 * {@link ImapMailboxPoller#pollMailbox} in its {@code finally} block.
 */
@Component
public class ImapPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImapPollingScheduler.class);

    /** Unique identifier for this application instance — used as the poll-lease owner. */
    private final String instanceId = UUID.randomUUID().toString();

    private final EmailMailboxService mailboxService;
    private final ImapMailboxPoller poller;
    private final long pollLeaseTtlSeconds;

    public ImapPollingScheduler(EmailMailboxService mailboxService,
                                ImapMailboxPoller poller,
                                @Value("${caseflow.email.imap.poll-lease-ttl-seconds:600}") long pollLeaseTtlSeconds) {
        this.mailboxService = mailboxService;
        this.poller = poller;
        this.pollLeaseTtlSeconds = pollLeaseTtlSeconds;
    }

    @Scheduled(fixedDelayString = "${caseflow.email.imap.scheduler-interval-ms:30000}")
    public void run() {
        List<EmailMailbox> mailboxes = mailboxService.findPollingEnabled();
        if (mailboxes.isEmpty()) return;

        log.debug("IMAP polling tick — checking {} polling-enabled mailbox(es) [instanceId: {}]",
                mailboxes.size(), instanceId);

        Instant now = Instant.now();
        Instant leaseExpiry = now.plusSeconds(pollLeaseTtlSeconds);

        for (EmailMailbox mailbox : mailboxes) {
            if (!isDue(mailbox, now)) continue;

            boolean claimed = mailboxService.tryClaimForPolling(mailbox.getId(), instanceId, leaseExpiry);
            if (!claimed) {
                log.debug("Mailbox {} is already being polled by another instance — skipping", mailbox.getId());
                continue;
            }

            try {
                poller.pollMailbox(mailbox);
            } catch (Exception e) {
                log.error("Unhandled error polling mailbox {} — {}", mailbox.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean isDue(EmailMailbox mailbox, Instant now) {
        if (mailbox.getLastPollAt() == null) return true;
        int intervalSeconds = mailbox.getPollIntervalSeconds() != null ? mailbox.getPollIntervalSeconds() : 60;
        return mailbox.getLastPollAt().plusSeconds(intervalSeconds).isBefore(now);
    }
}
