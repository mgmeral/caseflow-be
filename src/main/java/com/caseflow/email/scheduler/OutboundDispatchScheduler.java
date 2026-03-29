package com.caseflow.email.scheduler;

import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailMetrics;
import com.caseflow.email.service.SmtpEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled worker that sends PENDING outbound dispatches via SMTP.
 * Uses SKIP LOCKED to run safely on multiple instances.
 */
@Component
public class OutboundDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboundDispatchScheduler.class);
    private static final int BATCH_SIZE = 10;

    private final OutboundEmailDispatchRepository dispatchRepository;
    private final EmailDispatchService dispatchService;
    private final EmailMailboxRepository mailboxRepository;
    private final SmtpEmailSender smtpSender;
    private final EmailMetrics metrics;
    private final int maxAttempts;

    public OutboundDispatchScheduler(OutboundEmailDispatchRepository dispatchRepository,
                                      EmailDispatchService dispatchService,
                                      EmailMailboxRepository mailboxRepository,
                                      SmtpEmailSender smtpSender,
                                      EmailMetrics metrics,
                                      @Value("${caseflow.email.dispatch.max-attempts:3}") int maxAttempts) {
        this.dispatchRepository = dispatchRepository;
        this.dispatchService = dispatchService;
        this.mailboxRepository = mailboxRepository;
        this.smtpSender = smtpSender;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${caseflow.email.dispatch.retry-interval-ms:60000}")
    @Transactional
    public void sendPending() {
        if (!smtpSender.isConfigured()) {
            return; // SMTP not enabled — skip silently
        }

        List<OutboundEmailDispatch> pending = dispatchRepository.findAndLockPending(BATCH_SIZE);
        if (!pending.isEmpty()) {
            log.info("Sending {} PENDING outbound dispatches", pending.size());
        }
        for (OutboundEmailDispatch dispatch : pending) {
            trySend(dispatch);
        }
    }

    @Scheduled(fixedDelayString = "${caseflow.email.dispatch.retry-interval-ms:60000}",
               initialDelayString = "${caseflow.email.dispatch.retry-interval-ms:60000}")
    @Transactional
    public void retryFailed() {
        if (!smtpSender.isConfigured()) {
            return;
        }

        List<OutboundEmailDispatch> failed = dispatchRepository.findAndLockFailedForRetry(maxAttempts, BATCH_SIZE);
        if (!failed.isEmpty()) {
            log.info("Retrying {} FAILED outbound dispatches", failed.size());
        }
        for (OutboundEmailDispatch dispatch : failed) {
            trySend(dispatch);
        }
    }

    private void trySend(OutboundEmailDispatch dispatch) {
        dispatchService.markSending(dispatch);
        try {
            smtpSender.send(dispatch);
            dispatchService.markSent(dispatch);
            stampOutboundAt(dispatch.getFromAddress());
            metrics.outboundSent();
        } catch (EmailDispatchException e) {
            if (dispatch.getAttempts() >= maxAttempts) {
                dispatchService.markPermanentlyFailed(dispatch, e.getMessage());
                metrics.outboundPermanentlyFailed();
            } else {
                dispatchService.markFailed(dispatch, e.getMessage());
                metrics.outboundFailed();
            }
            log.warn("Dispatch {} failed (attempt {}): {}", dispatch.getId(), dispatch.getAttempts(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending dispatch {} — {}", dispatch.getId(), e.getMessage(), e);
            dispatchService.markFailed(dispatch, "Unexpected error: " + e.getMessage());
            metrics.outboundFailed();
        }
    }

    private void stampOutboundAt(String fromAddress) {
        if (fromAddress == null) return;
        mailboxRepository.findByAddress(fromAddress).ifPresent(m -> {
            m.setLastSuccessfulOutboundAt(Instant.now());
            mailboxRepository.save(m);
        });
    }
}
