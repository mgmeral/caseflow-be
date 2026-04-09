package com.caseflow.email.scheduled.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.ReplyThreadContext;
import com.caseflow.email.service.ReplyThreadContextResolver;
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
 * <p>Scheduled sends are thread-aware: when a {@code sourceEventId} is provided, the
 * recipient address, In-Reply-To, and References headers are derived from the inbound event
 * via {@link ReplyThreadContextResolver} — the same resolver used by immediate replies.
 *
 * <p>Dispatches use the existing {@link OutboundEmailDispatch} mechanism;
 * {@code isScheduledSend=true} and {@code scheduledAt} act as the not-before gate
 * in the scheduler query.
 *
 * <p>Pre-send revalidation is performed by
 * {@link com.caseflow.email.scheduler.OutboundDispatchScheduler} before SMTP dispatch.
 */
@Service
public class ScheduledEmailService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEmailService.class);

    /** Tickets in these statuses may not receive scheduled outbound emails. */
    private static final List<TicketStatus> BLOCKED_STATUSES = List.of(TicketStatus.CLOSED);

    private final EmailDispatchService dispatchService;
    private final EmailMailboxRepository mailboxRepository;
    private final TicketRepository ticketRepository;
    private final OutboundEmailDispatchRepository dispatchRepository;
    private final TicketHistoryService historyService;
    private final ReplyThreadContextResolver threadContextResolver;

    public ScheduledEmailService(EmailDispatchService dispatchService,
                                  EmailMailboxRepository mailboxRepository,
                                  TicketRepository ticketRepository,
                                  OutboundEmailDispatchRepository dispatchRepository,
                                  TicketHistoryService historyService,
                                  ReplyThreadContextResolver threadContextResolver) {
        this.dispatchService = dispatchService;
        this.mailboxRepository = mailboxRepository;
        this.ticketRepository = ticketRepository;
        this.dispatchRepository = dispatchRepository;
        this.historyService = historyService;
        this.threadContextResolver = threadContextResolver;
    }

    /**
     * Creates a thread-aware scheduled outbound email for the given ticket.
     *
     * <p>When {@code sourceEventId} is provided, the reply-to address and threading headers
     * (In-Reply-To, References) are derived from the inbound event. When absent,
     * {@code toAddress} must be provided (proactive outreach path).
     *
     * @param ticketPublicId the ticket to send from
     * @param mailboxId      mailbox to use for SMTP
     * @param sourceEventId  optional — inbound event being replied to; drives thread context
     * @param toAddress      explicit recipient; required when sourceEventId is absent
     * @param subject        email subject
     * @param textBody       plain-text body
     * @param htmlBody       HTML body (optional)
     * @param sendNotBefore  earliest time to dispatch this email
     * @param requestedBy    user who created the schedule
     * @param templateId     optional — explicit template id override
     * @param templateCode   optional — template code; ignored when templateId set
     * @param contentWasEdited true when the agent modified the rendered content before scheduling
     * @return the queued dispatch record
     */
    @Transactional
    public OutboundEmailDispatch scheduleEmail(UUID ticketPublicId, Long mailboxId,
                                               Long sourceEventId, String toAddress,
                                               String subject, String textBody, String htmlBody,
                                               Instant sendNotBefore, Long requestedBy,
                                               Long templateId, String templateCode,
                                               boolean contentWasEdited) {
        if (sendNotBefore.isBefore(Instant.now())) {
            throw new IllegalArgumentException(
                    "sendNotBefore must be in the future; got: " + sendNotBefore);
        }

        // Both sourceEventId and toAddress absent — cannot determine recipient
        if (sourceEventId == null && (toAddress == null || toAddress.isBlank())) {
            throw new IllegalArgumentException(
                    "Either sourceEventId or toAddress must be provided");
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

        // Resolve recipient address and threading headers from source event (or explicit override)
        ReplyThreadContext threadCtx = threadContextResolver.resolve(sourceEventId, toAddress);

        OutboundEmailDispatch dispatch = dispatchService.enqueueScheduled(
                ticket.getId(), mailboxId,
                threadCtx.sourceIngressEventId(), requestedBy,
                mailbox.getAddress(), threadCtx.resolvedToAddress(),
                threadCtx.resolvedToAddress(),
                subject, textBody, htmlBody,
                threadCtx.inReplyToMessageId(), threadCtx.referencesHeader(),
                sendNotBefore, templateId, templateCode,
                contentWasEdited
        );

        historyService.recordScheduledEmailCreated(ticket.getId(), ticket.getPublicId(),
                dispatch.getId(), threadCtx.resolvedToAddress(), sendNotBefore, requestedBy);

        log.info("Scheduled email created — dispatchId: {}, ticket: {}, to: '{}', sourceEvent: {}, sendAt: {}",
                dispatch.getId(), ticket.getTicketNo(), threadCtx.resolvedToAddress(),
                sourceEventId, sendNotBefore);
        return dispatch;
    }

    /**
     * Backward-compatible overload — no source event, explicit toAddress (proactive send).
     */
    @Transactional
    public OutboundEmailDispatch scheduleEmail(UUID ticketPublicId, Long mailboxId,
                                               String toAddress, String subject,
                                               String textBody, String htmlBody,
                                               Instant sendNotBefore, Long requestedBy) {
        return scheduleEmail(ticketPublicId, mailboxId,
                null, toAddress,
                subject, textBody, htmlBody,
                sendNotBefore, requestedBy,
                null, null, false);
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
     * Lists all scheduled (including sent and canceled) outbound emails for a ticket.
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
