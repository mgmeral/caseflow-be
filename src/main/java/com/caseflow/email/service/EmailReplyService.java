package com.caseflow.email.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


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
 * <h2>Template selection</h2>
 * Template selection priority:
 * <ol>
 *   <li>Explicit {@code templateId} → load that specific template.</li>
 *   <li>Explicit {@code templateCode} → load the active template by code.</li>
 *   <li>Default: active {@code CUSTOMER_REPLY} template.</li>
 *   <li>Fallback: built-in plain renderer if no DB template found.</li>
 * </ol>
 *
 * <h2>History events</h2>
 * Writes {@code OUTBOUND_REPLY_QUEUED} at enqueue time.
 * {@code OUTBOUND_REPLY_SENT} / {@code OUTBOUND_REPLY_FAILED} are written by the scheduler
 * after confirmed SMTP outcome.
 *
 * <h2>System status transition</h2>
 * The WAITING_CUSTOMER transition is applied by
 * {@link com.caseflow.email.scheduler.OutboundDispatchScheduler} AFTER the message
 * is successfully sent via SMTP — not at enqueue time.
 */
@Service
public class EmailReplyService {

    private static final Logger log = LoggerFactory.getLogger(EmailReplyService.class);

    private static final String DEFAULT_TEMPLATE_CODE = "CUSTOMER_REPLY";

    private final EmailDispatchService dispatchService;
    private final TicketRepository ticketRepository;
    private final TicketHistoryService historyService;
    private final EmailMetrics metrics;
    private final MailTemplateService mailTemplateService;
    private final ReplyThreadContextResolver threadContextResolver;

    public EmailReplyService(EmailDispatchService dispatchService,
                             TicketRepository ticketRepository,
                             TicketHistoryService historyService,
                             EmailMetrics metrics,
                             MailTemplateService mailTemplateService,
                             ReplyThreadContextResolver threadContextResolver) {
        this.dispatchService = dispatchService;
        this.ticketRepository = ticketRepository;
        this.historyService = historyService;
        this.metrics = metrics;
        this.mailTemplateService = mailTemplateService;
        this.threadContextResolver = threadContextResolver;
    }

    /**
     * Enqueues an outbound customer reply for ticket {@code ticketId}.
     *
     * @param templateId       optional — if set, overrides templateCode and the default
     * @param templateCode     optional — if set, used to look up active template; ignored when templateId set
     * @param contentWasEdited true when the agent modified the preview-rendered content before sending
     * @return the created {@link OutboundEmailDispatch}
     */
    @Transactional
    public OutboundEmailDispatch sendReply(Long ticketId, Long mailboxId, Long sourceEventId,
                                           String toAddressOverride, String fromAddress,
                                           String subject, String textBody, String htmlBody,
                                           String inReplyToMessageId, Long sentByUserId,
                                           Long templateId, String templateCode,
                                           boolean contentWasEdited) {

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        // Resolve reply-to address and RFC 2822 threading headers — validates source event ownership
        ReplyThreadContext threadCtx = threadContextResolver.resolveForTicket(sourceEventId, toAddressOverride, ticketId);
        String resolvedToAddress = threadCtx.resolvedToAddress();

        // Caller can explicitly override In-Reply-To (e.g. preview-to-send flow)
        String resolvedInReplyTo = (inReplyToMessageId != null && !inReplyToMessageId.isBlank())
                ? inReplyToMessageId
                : threadCtx.inReplyToMessageId();
        String referencesHeader = threadCtx.referencesHeader();

        log.info("SMTP_SEND enqueueing reply — ticketId: {}, to: '{}', mailboxId: {}, sentBy: {}",
                ticketId, resolvedToAddress, mailboxId, sentByUserId);

        // Resolve template: explicit id → explicit code → default CUSTOMER_REPLY → fallback
        MailTemplate resolvedTemplate = resolveTemplate(templateId, templateCode);
        log.info("TEMPLATE_RENDER ticket: {}, template: {}", ticketId,
                resolvedTemplate != null
                        ? resolvedTemplate.getCode() + "#" + resolvedTemplate.getId()
                        : "FALLBACK");

        String renderedText;
        String renderedHtml;
        if (resolvedTemplate != null) {
            renderedText = mailTemplateService.substitute(
                    resolvedTemplate.getPlainTextTemplate(), textBody, ticket.getTicketNo(),
                    null, null, null);
            String bodyForHtml = (htmlBody != null && !htmlBody.isBlank())
                    ? htmlBody
                    : mailTemplateService.escapeHtml(textBody);
            renderedHtml = mailTemplateService.substitute(
                    resolvedTemplate.getHtmlTemplate(), bodyForHtml, ticket.getTicketNo(),
                    null, null, null);
        } else {
            log.info("TEMPLATE_FALLBACK ticket: {} — no active template found in DB", ticketId);
            renderedText = (textBody != null ? textBody : "") + "\n\n---\nTicket: " + ticket.getTicketNo();
            renderedHtml = (htmlBody != null && !htmlBody.isBlank()) ? htmlBody : null;
        }

        Long resolvedTemplateId = resolvedTemplate != null ? resolvedTemplate.getId() : null;
        String resolvedTemplateCode = resolvedTemplate != null ? resolvedTemplate.getCode() : null;

        OutboundEmailDispatch dispatch = dispatchService.enqueue(
                ticketId, mailboxId, sourceEventId, sentByUserId,
                fromAddress, resolvedToAddress, resolvedToAddress,
                subject, renderedText, renderedHtml, resolvedInReplyTo, referencesHeader,
                resolvedTemplateId, resolvedTemplateCode, contentWasEdited);

        // Structured history event — replaces the old free-form CUSTOMER_REPLY_QUEUED
        historyService.recordOutboundReplyQueued(ticketId, ticket.getPublicId(),
                dispatch.getId(), resolvedToAddress, sentByUserId);

        // Record template usage if a DB template was applied
        if (resolvedTemplate != null) {
            historyService.recordTemplateUsed(ticketId, resolvedTemplate.getId(),
                    resolvedTemplate.getCode(), sentByUserId);
        }

        metrics.outboundQueued();

        // NOTE: WAITING_CUSTOMER system transition is applied in OutboundDispatchScheduler
        // after SMTP send succeeds — not here — to avoid false state on send failure.

        log.info("SMTP_SEND dispatch enqueued — ticketId: {}, dispatchId: {}, resolvedTo: '{}'",
                ticketId, dispatch.getId(), resolvedToAddress);
        return dispatch;
    }

    /** Backward-compatible overload — assumes content was not edited after preview. */
    @Transactional
    public OutboundEmailDispatch sendReply(Long ticketId, Long mailboxId, Long sourceEventId,
                                           String toAddressOverride, String fromAddress,
                                           String subject, String textBody, String htmlBody,
                                           String inReplyToMessageId, Long sentByUserId,
                                           Long templateId, String templateCode) {
        return sendReply(ticketId, mailboxId, sourceEventId, toAddressOverride, fromAddress,
                subject, textBody, htmlBody, inReplyToMessageId, sentByUserId,
                templateId, templateCode, false);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Template selection: explicit id → explicit code → default CUSTOMER_REPLY.
     * Returns null if no active template is found (triggers built-in fallback in caller).
     */
    private MailTemplate resolveTemplate(Long templateId, String templateCode) {
        if (templateId != null) {
            try {
                MailTemplate t = mailTemplateService.findById(templateId);
                if (Boolean.TRUE.equals(t.getIsActive())) return t;
                log.warn("TEMPLATE_WARN requested templateId {} is inactive — falling back", templateId);
            } catch (Exception e) {
                log.warn("TEMPLATE_WARN templateId {} not found — falling back: {}", templateId, e.getMessage());
            }
        }

        String codeToUse = (templateCode != null && !templateCode.isBlank())
                ? templateCode
                : DEFAULT_TEMPLATE_CODE;

        Optional<MailTemplate> byCode = mailTemplateService.findActiveByCode(codeToUse);
        if (byCode.isPresent()) return byCode.get();

        // If an explicit code was requested but not found, don't silently fall back to default
        if (templateCode != null && !templateCode.isBlank()
                && !templateCode.equals(DEFAULT_TEMPLATE_CODE)) {
            log.warn("TEMPLATE_WARN requested templateCode '{}' not found or inactive — using fallback", templateCode);
        }

        // Try default as last resort before returning null
        if (!DEFAULT_TEMPLATE_CODE.equals(codeToUse)) {
            return mailTemplateService.findActiveByCode(DEFAULT_TEMPLATE_CODE).orElse(null);
        }

        return null;
    }

}
