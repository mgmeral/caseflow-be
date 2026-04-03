package com.caseflow.email.scheduler;

import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.email.domain.DispatchFailureCategory;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailMetrics;
import com.caseflow.email.service.SmtpEmailSender;
import com.caseflow.workflow.state.TicketSystemTransitionService;
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
 *
 * <p>Uses SKIP LOCKED for safe multi-instance operation.
 * For each dispatch, loads the associated {@link EmailMailbox} to use
 * mailbox-specific SMTP settings. Falls back to global sender if no mailboxId is set.
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
    private final TicketSystemTransitionService systemTransitionService;
    private final int maxAttempts;

    public OutboundDispatchScheduler(OutboundEmailDispatchRepository dispatchRepository,
                                      EmailDispatchService dispatchService,
                                      EmailMailboxRepository mailboxRepository,
                                      SmtpEmailSender smtpSender,
                                      EmailMetrics metrics,
                                      TicketSystemTransitionService systemTransitionService,
                                      @Value("${caseflow.email.dispatch.max-attempts:3}") int maxAttempts) {
        this.dispatchRepository = dispatchRepository;
        this.dispatchService = dispatchService;
        this.mailboxRepository = mailboxRepository;
        this.smtpSender = smtpSender;
        this.metrics = metrics;
        this.systemTransitionService = systemTransitionService;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${caseflow.email.dispatch.retry-interval-ms:60000}")
    @Transactional
    public void sendPending() {
        List<OutboundEmailDispatch> pending = dispatchRepository.findAndLockPending(BATCH_SIZE);
        if (!pending.isEmpty()) {
            log.info("SMTP_SEND sending {} PENDING outbound dispatches", pending.size());
        }
        for (OutboundEmailDispatch dispatch : pending) {
            trySend(dispatch);
        }
    }

    @Scheduled(fixedDelayString = "${caseflow.email.dispatch.retry-interval-ms:60000}",
               initialDelayString = "${caseflow.email.dispatch.retry-interval-ms:60000}")
    @Transactional
    public void retryFailed() {
        List<OutboundEmailDispatch> failed = dispatchRepository.findAndLockFailedForRetry(maxAttempts, BATCH_SIZE);
        if (!failed.isEmpty()) {
            log.info("SMTP_SEND retrying {} FAILED outbound dispatches", failed.size());
        }
        for (OutboundEmailDispatch dispatch : failed) {
            trySend(dispatch);
        }
    }

    private void trySend(OutboundEmailDispatch dispatch) {
        EmailMailbox mailbox = resolveMailbox(dispatch);

        if (mailbox != null && !Boolean.TRUE.equals(mailbox.getIsActive())) {
            String reason = "Mailbox " + mailbox.getId() + " is inactive";
            dispatchService.markPermanentlyFailed(dispatch, reason, DispatchFailureCategory.MAILBOX_INACTIVE);
            log.warn("SMTP_SEND dispatch {} permanently failed — mailbox inactive (mailboxId: {})",
                    dispatch.getId(), mailbox.getId());
            metrics.outboundPermanentlyFailed();
            return;
        }

        dispatchService.markSending(dispatch);
        try {
            smtpSender.send(dispatch, mailbox);
            dispatchService.markSent(dispatch);
            stampOutboundAt(mailbox, dispatch.getFromAddress());
            metrics.outboundSent();
            log.info("SMTP_SEND_SUCCESS dispatch {} sent — ticketId: {}, to: '{}'",
                    dispatch.getId(), dispatch.getTicketId(), dispatch.getToAddress());
            // Apply WAITING_CUSTOMER transition only after confirmed SMTP success
            if (dispatch.getTicketId() != null) {
                systemTransitionService.applyOutboundReplyTransition(dispatch.getTicketId(), dispatch.getId());
            }
        } catch (EmailDispatchException e) {
            DispatchFailureCategory category = e.getCategory();
            boolean isPermanent = e.getCategory() == DispatchFailureCategory.UNCONFIGURED
                    || e.getCategory() == DispatchFailureCategory.MAILBOX_INACTIVE
                    || dispatch.getAttempts() >= maxAttempts;

            if (isPermanent) {
                dispatchService.markPermanentlyFailed(dispatch, e.getMessage(), category);
                metrics.outboundPermanentlyFailed();
            } else {
                dispatchService.markFailed(dispatch, e.getMessage(), category);
                metrics.outboundFailed();
            }
            log.warn("SMTP_SEND_FAILURE dispatch {} (attempt {}, category: {}): {}",
                    dispatch.getId(), dispatch.getAttempts(), category, e.getMessage());
        } catch (Exception e) {
            log.error("SMTP_SEND unexpected error sending dispatch {} — {}",
                    dispatch.getId(), e.getMessage(), e);
            dispatchService.markFailed(dispatch, "Unexpected error: " + e.getMessage(),
                    DispatchFailureCategory.UNKNOWN);
            metrics.outboundFailed();
        }
    }

    private EmailMailbox resolveMailbox(OutboundEmailDispatch dispatch) {
        if (dispatch.getMailboxId() != null) {
            return mailboxRepository.findById(dispatch.getMailboxId()).orElse(null);
        }
        // Legacy dispatches without mailboxId: try to find by fromAddress
        if (dispatch.getFromAddress() != null) {
            return mailboxRepository.findByAddress(dispatch.getFromAddress()).orElse(null);
        }
        return null;
    }

    private void stampOutboundAt(EmailMailbox mailbox, String fromAddress) {
        if (mailbox != null) {
            mailbox.setLastSuccessfulOutboundAt(Instant.now());
            mailboxRepository.save(mailbox);
            return;
        }
        if (fromAddress != null) {
            mailboxRepository.findByAddress(fromAddress).ifPresent(m -> {
                m.setLastSuccessfulOutboundAt(Instant.now());
                mailboxRepository.save(m);
            });
        }
    }
}
