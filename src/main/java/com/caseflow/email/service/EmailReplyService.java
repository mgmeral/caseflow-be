package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates outbound customer replies for a ticket.
 *
 * <h2>Reply-target derivation</h2>
 * When a {@code sourceEventId} is provided:
 * <ol>
 *   <li>Load the source {@link EmailIngressEvent}.</li>
 *   <li>Use {@code replyTo} header if present, otherwise {@code from} header.</li>
 *   <li>Store the resolved address as {@code resolvedToAddress} for audit.</li>
 * </ol>
 * When no {@code sourceEventId} is provided, {@code toAddressOverride} must be supplied
 * (proactive outreach path).
 *
 * <h2>System status transition</h2>
 * The WAITING_CUSTOMER transition is applied by {@link com.caseflow.email.scheduler.OutboundDispatchScheduler}
 * AFTER the message is successfully sent via SMTP — not at enqueue time.
 * This avoids marking the ticket as waiting-customer when the send can still fail.
 */
@Service
public class EmailReplyService {

    private static final Logger log = LoggerFactory.getLogger(EmailReplyService.class);

    private final EmailDispatchService dispatchService;
    private final TicketRepository ticketRepository;
    private final EmailIngressEventRepository ingressEventRepository;
    private final TicketHistoryService historyService;
    private final EmailMetrics metrics;
    private final MailTemplateService mailTemplateService;

    public EmailReplyService(EmailDispatchService dispatchService,
                             TicketRepository ticketRepository,
                             EmailIngressEventRepository ingressEventRepository,
                             TicketHistoryService historyService,
                             EmailMetrics metrics,
                             MailTemplateService mailTemplateService) {
        this.dispatchService = dispatchService;
        this.ticketRepository = ticketRepository;
        this.ingressEventRepository = ingressEventRepository;
        this.historyService = historyService;
        this.metrics = metrics;
        this.mailTemplateService = mailTemplateService;
    }

    /**
     * Enqueues an outbound customer reply for ticket {@code ticketId}.
     *
     * @return the created {@link OutboundEmailDispatch} (dispatchId, resolvedToAddress, etc.)
     */
    @Transactional
    public OutboundEmailDispatch sendReply(Long ticketId, Long mailboxId, Long sourceEventId,
                                           String toAddressOverride, String fromAddress,
                                           String subject, String textBody, String htmlBody,
                                           String inReplyToMessageId, Long sentByUserId) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        // Load source event once — used for reply-target derivation AND threading headers
        EmailIngressEvent sourceEvent = null;
        if (sourceEventId != null) {
            sourceEvent = ingressEventRepository.findById(sourceEventId)
                    .orElseThrow(() -> new IngressEventNotFoundException(sourceEventId));
        }

        String resolvedToAddress = resolveReplyTarget(sourceEvent, sourceEventId, toAddressOverride);

        // Derive RFC 2822 threading headers from source event when not explicitly provided
        String resolvedInReplyTo = inReplyToMessageId;
        String referencesHeader = null;
        if (sourceEvent != null) {
            if (resolvedInReplyTo == null || resolvedInReplyTo.isBlank()) {
                resolvedInReplyTo = sourceEvent.getMessageId();
            }
            // Build References chain: prior chain + source messageId
            String priorRefs = sourceEvent.getRawReferences();
            if (priorRefs != null && !priorRefs.isBlank()) {
                // rawReferences is stored pipe-separated; RFC 2822 References is space-separated
                referencesHeader = priorRefs.replace("|", " ").trim()
                        + " " + sourceEvent.getMessageId();
            } else {
                referencesHeader = sourceEvent.getMessageId();
            }
        }

        log.info("SMTP_SEND enqueueing reply — ticketId: {}, to: '{}', mailboxId: {}, sentBy: {}",
                ticketId, resolvedToAddress, mailboxId, sentByUserId);

        // Apply CUSTOMER_REPLY template if one is active in the DB; otherwise use raw bodies
        java.util.Optional<MailTemplate> dbTemplate = mailTemplateService.findActiveByCode("CUSTOMER_REPLY");
        log.info("TEMPLATE_RENDER ticket: {}, template: {}", ticketId,
                dbTemplate.map(t -> t.getCode() + "#" + t.getId()).orElse("FALLBACK"));
        String renderedText;
        String renderedHtml;
        if (dbTemplate.isPresent()) {
            MailTemplate tpl = dbTemplate.get();
            renderedText = mailTemplateService.substitute(
                    tpl.getPlainTextTemplate(), textBody, ticket.getTicketNo(), null, null, null);
            // For the HTML template: prefer an explicit htmlBody; otherwise HTML-escape the plain textBody
            // so that '<', '>' etc. are not misinterpreted by email clients as HTML tags.
            String bodyForHtml = (htmlBody != null && !htmlBody.isBlank())
                    ? htmlBody
                    : mailTemplateService.escapeHtml(textBody);
            renderedHtml = mailTemplateService.substitute(
                    tpl.getHtmlTemplate(), bodyForHtml, ticket.getTicketNo(), null, null, null);
        } else {
            log.info("TEMPLATE_FALLBACK ticket: {} — no active CUSTOMER_REPLY template in DB", ticketId);
            renderedText = (textBody != null ? textBody : "") + "\n\n---\nTicket: " + ticket.getTicketNo();
            renderedHtml = (htmlBody != null && !htmlBody.isBlank()) ? htmlBody : null;
        }

        OutboundEmailDispatch dispatch = dispatchService.enqueue(
                ticketId, mailboxId, sourceEventId, sentByUserId,
                fromAddress, resolvedToAddress, resolvedToAddress,
                subject, renderedText, renderedHtml, resolvedInReplyTo, referencesHeader);

        historyService.record(ticketId, "CUSTOMER_REPLY_QUEUED", sentByUserId,
                "to=" + resolvedToAddress + ";dispatchId=" + dispatch.getId());
        metrics.outboundQueued();

        // NOTE: WAITING_CUSTOMER system transition is applied in OutboundDispatchScheduler
        // after SMTP send succeeds — not here — to avoid false state on send failure.

        log.info("SMTP_SEND dispatch enqueued — ticketId: {}, dispatchId: {}, resolvedTo: '{}'",
                ticketId, dispatch.getId(), resolvedToAddress);
        return dispatch;
    }

    private String resolveReplyTarget(EmailIngressEvent sourceEvent, Long sourceEventId,
                                      String toAddressOverride) {
        if (sourceEvent != null) {
            String derived = sourceEvent.effectiveReplyTo();
            if (derived == null || derived.isBlank()) {
                throw new IllegalArgumentException(
                        "Cannot derive reply target: source event " + sourceEventId
                                + " has no From or Reply-To header");
            }
            return extractEmailAddress(derived);
        }

        if (toAddressOverride != null && !toAddressOverride.isBlank()) {
            return toAddressOverride;
        }

        throw new IllegalArgumentException(
                "Reply target cannot be determined: provide sourceEventId or toAddress");
    }

    /** Strips display name from "Display Name <email@host>" → "email@host". */
    private String extractEmailAddress(String raw) {
        if (raw == null) return null;
        int start = raw.lastIndexOf('<');
        int end = raw.lastIndexOf('>');
        if (start >= 0 && end > start) {
            return raw.substring(start + 1, end).trim();
        }
        return raw.trim();
    }
}
