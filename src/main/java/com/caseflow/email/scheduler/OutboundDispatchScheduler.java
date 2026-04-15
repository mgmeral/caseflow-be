package com.caseflow.email.scheduler;

import com.caseflow.common.exception.EmailDispatchException;
import com.caseflow.email.domain.DispatchFailureCategory;
import com.caseflow.email.domain.DispatchStatus;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailMetrics;
import com.caseflow.email.service.SmtpEmailSender;
import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketSystemTransitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    private final TicketRepository ticketRepository;
    private final SmtpEmailSender smtpSender;
    private final EmailMetrics metrics;
    private final TicketSystemTransitionService systemTransitionService;
    private final TicketHistoryService historyService;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxAttempts;

    public OutboundDispatchScheduler(OutboundEmailDispatchRepository dispatchRepository,
                                      EmailDispatchService dispatchService,
                                      EmailMailboxRepository mailboxRepository,
                                      TicketRepository ticketRepository,
                                      SmtpEmailSender smtpSender,
                                      EmailMetrics metrics,
                                      TicketSystemTransitionService systemTransitionService,
                                      TicketHistoryService historyService,
                                      ApplicationEventPublisher eventPublisher,
                                      @Value("${caseflow.email.dispatch.max-attempts:3}") int maxAttempts) {
        this.dispatchRepository = dispatchRepository;
        this.dispatchService = dispatchService;
        this.mailboxRepository = mailboxRepository;
        this.ticketRepository = ticketRepository;
        this.smtpSender = smtpSender;
        this.metrics = metrics;
        this.systemTransitionService = systemTransitionService;
        this.historyService = historyService;
        this.eventPublisher = eventPublisher;
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
        // For scheduled sends, re-validate before dispatching
        if (Boolean.TRUE.equals(dispatch.getIsScheduledSend())) {
            String blockReason = revalidateScheduledSend(dispatch);
            if (blockReason != null) {
                dispatchService.markPermanentlyFailed(dispatch, blockReason,
                        DispatchFailureCategory.REVALIDATION_FAILURE);
                log.warn("SMTP_SEND scheduled dispatch {} blocked by revalidation: {}",
                        dispatch.getId(), blockReason);
                if (dispatch.getTicketId() != null) {
                    historyService.recordScheduledEmailFailed(dispatch.getTicketId(),
                            dispatch.getId(), blockReason);
                }
                return;
            }
        }

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
            // Record successful send event in ticket history
            if (dispatch.getTicketId() != null) {
                historyService.recordOutboundReplySent(dispatch.getTicketId(), dispatch.getId());
                Ticket sentTicket = ticketRepository.findById(dispatch.getTicketId()).orElse(null);
                if (sentTicket != null) {
                    // Stamp first-response time on the ticket if this is the first outbound reply
                    if (sentTicket.getFirstResponseRespondedAt() == null) {
                        sentTicket.setFirstResponseRespondedAt(Instant.now());
                        ticketRepository.save(sentTicket);
                        log.info("SLA_FIRST_RESPONSE stamped — ticketId: {}, dispatchId: {}",
                                sentTicket.getId(), dispatch.getId());
                    }
                    if (Boolean.TRUE.equals(dispatch.getIsScheduledSend())) {
                        historyService.recordScheduledEmailSent(
                                sentTicket.getId(), sentTicket.getPublicId(), dispatch.getId());
                    }
                }
            }
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
            // Record failure event in ticket history
            if (dispatch.getTicketId() != null) {
                historyService.recordOutboundReplyFailed(dispatch.getTicketId(), dispatch.getId(),
                        e.getMessage(), isPermanent);
                if (isPermanent) {
                    Ticket t = ticketRepository.findById(dispatch.getTicketId()).orElse(null);
                    if (t != null) {
                        eventPublisher.publishEvent(new TicketDomainEvent(t.getId(), t.getPublicId(),
                                NotificationEventType.OUTBOUND_REPLY_FAILED, null,
                                t.getCustomerId(), t.getAssignedGroupId()));
                    }
                }
            }
            log.warn("SMTP_SEND_FAILURE dispatch {} (attempt {}, category: {}): {}",
                    dispatch.getId(), dispatch.getAttempts(), category, e.getMessage());
        } catch (Exception e) {
            log.error("SMTP_SEND unexpected error sending dispatch {} — {}",
                    dispatch.getId(), e.getMessage(), e);
            dispatchService.markFailed(dispatch, "Unexpected error: " + e.getMessage(),
                    DispatchFailureCategory.UNKNOWN);
            metrics.outboundFailed();
            if (dispatch.getTicketId() != null) {
                historyService.recordOutboundReplyFailed(dispatch.getTicketId(), dispatch.getId(),
                        "Unexpected error: " + e.getMessage(), false);
            }
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

    /**
     * Validates a scheduled dispatch before send.
     *
     * @return non-null failure reason if the send should be blocked; null if OK to proceed
     */
    private String revalidateScheduledSend(OutboundEmailDispatch dispatch) {
        // Must not have been canceled since scheduling
        if (dispatch.getStatus() == DispatchStatus.CANCELED) {
            return "Dispatch was canceled";
        }
        // Ticket must still exist and be in a sendable state
        if (dispatch.getTicketId() != null) {
            Ticket ticket = ticketRepository.findById(dispatch.getTicketId()).orElse(null);
            if (ticket == null) {
                return "Ticket " + dispatch.getTicketId() + " no longer exists";
            }
            if (ticket.getStatus() == TicketStatus.CLOSED) {
                return "Ticket is CLOSED — scheduled email cannot be sent";
            }
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
