package com.caseflow.email.scheduled.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages scheduled outbound email dispatches.
 *
 * <p>Scheduled sends reuse the existing {@link OutboundEmailDispatch} mechanism —
 * the {@code scheduledAt} field acts as a "not-before" gate in the scheduler query.
 * The {@code isScheduledSend} flag distinguishes them from immediate replies.
 *
 * <p>Pre-send revalidation is performed by
 * {@link com.caseflow.email.scheduler.OutboundDispatchScheduler} before SMTP dispatch.
 */
@Service
public class ScheduledEmailService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEmailService.class);

    /** Tickets in these statuses may not receive scheduled outbound emails. */
    private static final List<TicketStatus> BLOCKED_STATUSES =
            List.of(TicketStatus.CLOSED);

    private final EmailDispatchService dispatchService;
    private final EmailMailboxRepository mailboxRepository;
    private final TicketRepository ticketRepository;
    private final OutboundEmailDispatchRepository dispatchRepository;
    private final TicketHistoryService historyService;

    public ScheduledEmailService(EmailDispatchService dispatchService,
                                  EmailMailboxRepository mailboxRepository,
                                  TicketRepository ticketRepository,
                                  OutboundEmailDispatchRepository dispatchRepository,
                                  TicketHistoryService historyService) {
        this.dispatchService = dispatchService;
        this.mailboxRepository = mailboxRepository;
        this.ticketRepository = ticketRepository;
        this.dispatchRepository = dispatchRepository;
        this.historyService = historyService;
    }

    /**
     * Creates a scheduled outbound email for the given ticket.
     *
     * @param ticketPublicId the ticket to send from
     * @param mailboxId      mailbox to use for SMTP
     * @param toAddress      recipient address
     * @param subject        email subject
     * @param textBody       plain-text body
     * @param htmlBody       HTML body (optional)
     * @param sendNotBefore  earliest time to dispatch this email
     * @param requestedBy    user who created the schedule
     * @return the queued dispatch record
     */
    @Transactional
    public OutboundEmailDispatch scheduleEmail(UUID ticketPublicId, Long mailboxId,
                                               String toAddress, String subject,
                                               String textBody, String htmlBody,
                                               Instant sendNotBefore, Long requestedBy) {
        if (sendNotBefore.isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "sendNotBefore must be in the future; got: " + sendNotBefore);
        }

        Ticket ticket = ticketRepository.findByPublicId(ticketPublicId)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket not found: " + ticketPublicId));

        if (BLOCKED_STATUSES.contains(ticket.getStatus())) {
            throw new IllegalStateException(
                    "Cannot schedule an email on a " + ticket.getStatus() + " ticket");
        }

        EmailMailbox mailbox = mailboxRepository.findById(mailboxId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mailbox not found: " + mailboxId));

        if (!Boolean.TRUE.equals(mailbox.getIsActive())) {
            throw new IllegalStateException("Mailbox " + mailboxId + " is inactive");
        }

        if (mailbox.getSmtpHost() == null || mailbox.getSmtpHost().isBlank()) {
            throw new IllegalStateException("Mailbox " + mailboxId + " has no SMTP configuration");
        }

        OutboundEmailDispatch dispatch = dispatchService.enqueueScheduled(
                ticket.getId(), mailboxId, requestedBy,
                mailbox.getAddress(), toAddress,
                subject, textBody, htmlBody,
                sendNotBefore, null, null, false
        );

        historyService.recordScheduledEmailCreated(ticket.getId(), ticket.getPublicId(),
                dispatch.getId(), toAddress, sendNotBefore, requestedBy);

        log.info("Scheduled email created — dispatchId: {}, ticket: {}, to: {}, sendAt: {}",
                dispatch.getId(), ticket.getTicketNo(), toAddress, sendNotBefore);
        return dispatch;
    }

    /**
     * Cancels a pending scheduled email dispatch.
     */
    @Transactional
    public OutboundEmailDispatch cancelScheduledEmail(UUID ticketPublicId, Long dispatchId,
                                                       Long canceledBy) {
        Ticket ticket = ticketRepository.findByPublicId(ticketPublicId)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket not found: " + ticketPublicId));

        OutboundEmailDispatch dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Dispatch not found: " + dispatchId));

        if (!Boolean.TRUE.equals(dispatch.getIsScheduledSend())) {
            throw new IllegalArgumentException(
                    "Dispatch " + dispatchId + " is not a scheduled send");
        }

        // Security: dispatch must belong to this ticket
        if (!java.util.Objects.equals(ticket.getId(), dispatch.getTicketId())) {
            throw new IllegalArgumentException(
                    "Dispatch " + dispatchId + " does not belong to ticket " + ticketPublicId);
        }

        dispatchService.markCanceled(dispatch);

        historyService.recordScheduledEmailCanceled(ticket.getId(), ticket.getPublicId(),
                dispatch.getId(), canceledBy);

        log.info("Scheduled email canceled — dispatchId: {}, ticket: {}, canceledBy: {}",
                dispatchId, ticket.getTicketNo(), canceledBy);
        return dispatch;
    }

    /**
     * Lists all scheduled (pending) outbound emails for a ticket.
     */
    @Transactional(readOnly = true)
    public List<OutboundEmailDispatch> listScheduledForTicket(UUID ticketPublicId) {
        Ticket ticket = ticketRepository.findByPublicId(ticketPublicId)
                .orElseThrow(() -> new TicketNotFoundException(
                        "Ticket not found: " + ticketPublicId));
        return dispatchRepository
                .findByTicketIdAndIsScheduledSendTrueOrderByScheduledAtDesc(ticket.getId());
    }
}
