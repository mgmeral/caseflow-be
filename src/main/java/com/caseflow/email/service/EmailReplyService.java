package com.caseflow.email.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates outbound customer replies for a ticket.
 *
 * <p>Callers provide the reply content; this service enqueues a durable
 * {@link OutboundEmailDispatch} and records a history event.
 */
@Service
public class EmailReplyService {

    private static final Logger log = LoggerFactory.getLogger(EmailReplyService.class);

    private final EmailDispatchService dispatchService;
    private final TicketRepository ticketRepository;
    private final TicketHistoryService historyService;
    private final EmailMetrics metrics;

    public EmailReplyService(EmailDispatchService dispatchService,
                             TicketRepository ticketRepository,
                             TicketHistoryService historyService,
                             EmailMetrics metrics) {
        this.dispatchService = dispatchService;
        this.ticketRepository = ticketRepository;
        this.historyService = historyService;
        this.metrics = metrics;
    }

    /**
     * Enqueues an outbound customer reply for ticket {@code ticketId}.
     *
     * @param ticketId            target ticket
     * @param fromAddress         reply-from address (the CaseFlow mailbox address)
     * @param toAddress           customer email address
     * @param subject             reply subject (caller should prefix "Re: " if needed)
     * @param textBody            plain-text body
     * @param htmlBody            HTML body (optional)
     * @param inReplyToMessageId  original messageId to thread the reply
     * @param sentBy              userId who initiated the reply
     */
    @Transactional
    public void sendReply(Long ticketId, String fromAddress, String toAddress,
                          String subject, String textBody, String htmlBody,
                          String inReplyToMessageId, Long sentBy) {
        log.info("Enqueueing reply — ticketId: {}, to: '{}', sentBy: {}", ticketId, toAddress, sentBy);
        ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        dispatchService.enqueue(ticketId, fromAddress, toAddress, subject,
                textBody, htmlBody, inReplyToMessageId);

        historyService.record(ticketId, "CUSTOMER_REPLY_QUEUED", sentBy,
                "to=" + toAddress);
        metrics.outboundQueued();
        log.info("Reply enqueued — ticketId: {}, to: '{}'", ticketId, toAddress);
    }
}
