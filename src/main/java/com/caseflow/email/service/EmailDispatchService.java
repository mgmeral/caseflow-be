package com.caseflow.email.service;

import com.caseflow.common.exception.DispatchNotFoundException;
import com.caseflow.email.domain.DispatchFailureCategory;
import com.caseflow.email.domain.DispatchStatus;
import com.caseflow.email.domain.OutboundEmailDispatch;
import com.caseflow.email.repository.OutboundEmailDispatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists and manages the outbound email dispatch queue.
 * Actual SMTP sending is handled by {@link SmtpEmailSender}.
 */
@Service
public class EmailDispatchService {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);

    private final OutboundEmailDispatchRepository dispatchRepository;

    public EmailDispatchService(OutboundEmailDispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    /**
     * Enqueues an outbound email for durable dispatch.
     *
     * @param ticketId             target ticket
     * @param mailboxId            mailbox used to send (drives SMTP settings selection)
     * @param sourceIngressEventId source inbound event being replied to (null for proactive sends)
     * @param sentByUserId         agent who initiated the reply (null for system-generated)
     * @param fromAddress          sender address (the mailbox address)
     * @param toAddress            resolved recipient address
     * @param resolvedToAddress    backend-derived reply target (may equal toAddress or differ if FE override)
     * @param subject              email subject
     * @param textBody             plain-text body
     * @param htmlBody             HTML body (optional)
     * @param inReplyToMessageId   original messageId for In-Reply-To threading
     * @param referencesHeader     full RFC 2822 References chain (space-separated message-ids)
     */
    @Transactional
    public OutboundEmailDispatch enqueue(Long ticketId, Long mailboxId, Long sourceIngressEventId,
                                         Long sentByUserId, String fromAddress, String toAddress,
                                         String resolvedToAddress, String subject,
                                         String textBody, String htmlBody, String inReplyToMessageId,
                                         String referencesHeader) {
        return enqueue(ticketId, mailboxId, sourceIngressEventId, sentByUserId, fromAddress,
                toAddress, resolvedToAddress, subject, textBody, htmlBody,
                inReplyToMessageId, referencesHeader, null, null, false);
    }

    /**
     * Enqueues an outbound email with template audit metadata.
     *
     * @param appliedTemplateId   the mail_templates PK if a DB template was applied; null otherwise
     * @param appliedTemplateCode denormalised code for audit; null if no template
     * @param contentWasEdited    true when the agent modified the rendered output before send
     */
    @Transactional
    public OutboundEmailDispatch enqueue(Long ticketId, Long mailboxId, Long sourceIngressEventId,
                                         Long sentByUserId, String fromAddress, String toAddress,
                                         String resolvedToAddress, String subject,
                                         String textBody, String htmlBody, String inReplyToMessageId,
                                         String referencesHeader,
                                         Long appliedTemplateId, String appliedTemplateCode,
                                         boolean contentWasEdited) {
        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setTicketId(ticketId);
        dispatch.setMailboxId(mailboxId);
        dispatch.setSourceIngressEventId(sourceIngressEventId);
        dispatch.setSentByUserId(sentByUserId);
        dispatch.setMessageId(generateMessageId());
        dispatch.setFromAddress(fromAddress);
        dispatch.setToAddress(toAddress);
        dispatch.setResolvedToAddress(resolvedToAddress);
        dispatch.setSubject(subject);
        dispatch.setTextBody(textBody);
        dispatch.setHtmlBody(htmlBody);
        dispatch.setInReplyToMessageId(inReplyToMessageId);
        dispatch.setReferencesHeader(referencesHeader);
        dispatch.setAppliedTemplateId(appliedTemplateId);
        dispatch.setAppliedTemplateCode(appliedTemplateCode);
        dispatch.setContentWasEdited(contentWasEdited);
        dispatch.setStatus(DispatchStatus.PENDING);
        OutboundEmailDispatch saved = dispatchRepository.save(dispatch);
        log.info("SMTP_SEND dispatch enqueued — dispatchId: {}, to: '{}', resolvedTo: '{}', mailboxId: {}, ticketId: {}, template: {}",
                saved.getId(), toAddress, resolvedToAddress, mailboxId, ticketId, appliedTemplateCode);
        return saved;
    }

    @Transactional
    public void markSending(OutboundEmailDispatch dispatch) {
        dispatch.setStatus(DispatchStatus.SENDING);
        dispatch.setAttempts(dispatch.getAttempts() + 1);
        dispatch.setLastAttemptAt(Instant.now());
        dispatchRepository.save(dispatch);
    }

    @Transactional
    public void markSent(OutboundEmailDispatch dispatch) {
        dispatch.setStatus(DispatchStatus.SENT);
        dispatch.setSentAt(Instant.now());
        dispatchRepository.save(dispatch);
    }

    @Transactional
    public void markFailed(OutboundEmailDispatch dispatch, String reason,
                           DispatchFailureCategory category) {
        dispatch.setStatus(DispatchStatus.FAILED);
        dispatch.setFailureReason(reason);
        dispatch.setFailureCategory(category);
        dispatchRepository.save(dispatch);
    }

    @Transactional
    public void markPermanentlyFailed(OutboundEmailDispatch dispatch, String reason,
                                       DispatchFailureCategory category) {
        dispatch.setStatus(DispatchStatus.PERMANENTLY_FAILED);
        dispatch.setFailureReason(reason);
        dispatch.setFailureCategory(category);
        dispatchRepository.save(dispatch);
        log.error("SMTP_SEND dispatch permanently failed — dispatchId: {}, category: {}, reason: {}",
                dispatch.getId(), category, reason);
    }

    @Transactional(readOnly = true)
    public OutboundEmailDispatch getById(Long id) {
        return dispatchRepository.findById(id)
                .orElseThrow(() -> new DispatchNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<OutboundEmailDispatch> findByTicketId(Long ticketId) {
        return dispatchRepository.findByTicketId(ticketId);
    }

    @Transactional
    public void markCanceled(OutboundEmailDispatch dispatch) {
        if (dispatch.getStatus() != DispatchStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot cancel dispatch " + dispatch.getId()
                            + " in status " + dispatch.getStatus());
        }
        dispatch.setStatus(DispatchStatus.CANCELED);
        dispatch.setCanceledAt(Instant.now());
        dispatchRepository.save(dispatch);
        log.info("SMTP_SEND dispatch canceled — dispatchId: {}", dispatch.getId());
    }

    /**
     * Re-queues a failed dispatch for retry.
     *
     * <p>Resets status to {@link DispatchStatus#PENDING}, clears failure metadata,
     * and resets {@code scheduledAt} to now so the next scheduler tick picks it up.
     * Only dispatches in {@code FAILED} or {@code PERMANENTLY_FAILED} state may be retried.
     *
     * @param dispatch the dispatch to retry
     * @throws IllegalStateException if the dispatch is not in a retryable state
     */
    @Transactional
    public void retryDispatch(OutboundEmailDispatch dispatch) {
        if (dispatch.getStatus() != DispatchStatus.FAILED
                && dispatch.getStatus() != DispatchStatus.PERMANENTLY_FAILED) {
            throw new IllegalStateException(
                    "Cannot retry dispatch " + dispatch.getId()
                            + " in status " + dispatch.getStatus()
                            + ". Only FAILED or PERMANENTLY_FAILED dispatches may be retried.");
        }
        dispatch.setStatus(DispatchStatus.PENDING);
        dispatch.setFailureReason(null);
        dispatch.setFailureCategory(null);
        dispatch.setScheduledAt(Instant.now());
        dispatchRepository.save(dispatch);
        log.info("SMTP_SEND dispatch queued for retry — dispatchId: {}, previousStatus: {}",
                dispatch.getId(), dispatch.getStatus());
    }

    /**
     * Enqueues a scheduled outbound email with full thread-aware context.
     *
     * @param sourceIngressEventId inbound event being replied to; null for proactive scheduled sends
     * @param resolvedToAddress    backend-derived recipient (from source event or explicit)
     * @param inReplyToMessageId   RFC 2822 In-Reply-To header; null for proactive sends
     * @param referencesHeader     RFC 2822 References chain (space-separated); null for proactive sends
     */
    @Transactional
    public OutboundEmailDispatch enqueueScheduled(Long ticketId, Long mailboxId,
                                                    Long sourceIngressEventId, Long sentByUserId,
                                                    String fromAddress, String toAddress,
                                                    String resolvedToAddress,
                                                    String subject, String textBody, String htmlBody,
                                                    String inReplyToMessageId, String referencesHeader,
                                                    Instant sendNotBefore,
                                                    Long appliedTemplateId, String appliedTemplateCode,
                                                    boolean contentWasEdited) {
        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setTicketId(ticketId);
        dispatch.setMailboxId(mailboxId);
        dispatch.setSourceIngressEventId(sourceIngressEventId);
        dispatch.setSentByUserId(sentByUserId);
        dispatch.setMessageId(generateMessageId());
        dispatch.setFromAddress(fromAddress);
        dispatch.setToAddress(toAddress);
        dispatch.setResolvedToAddress(resolvedToAddress);
        dispatch.setSubject(subject);
        dispatch.setTextBody(textBody);
        dispatch.setHtmlBody(htmlBody);
        dispatch.setInReplyToMessageId(inReplyToMessageId);
        dispatch.setReferencesHeader(referencesHeader);
        dispatch.setAppliedTemplateId(appliedTemplateId);
        dispatch.setAppliedTemplateCode(appliedTemplateCode);
        dispatch.setContentWasEdited(contentWasEdited);
        dispatch.setIsScheduledSend(Boolean.TRUE);
        dispatch.setScheduledAt(sendNotBefore);
        dispatch.setStatus(DispatchStatus.PENDING);
        OutboundEmailDispatch saved = dispatchRepository.save(dispatch);
        log.info("SMTP_SEND scheduled dispatch enqueued — dispatchId: {}, to: '{}', resolvedTo: '{}', sendAt: {}, ticketId: {}, sourceEvent: {}",
                saved.getId(), toAddress, resolvedToAddress, sendNotBefore, ticketId, sourceIngressEventId);
        return saved;
    }

    /**
     * Backward-compatible overload — no threading headers (proactive send).
     */
    @Transactional
    public OutboundEmailDispatch enqueueScheduled(Long ticketId, Long mailboxId,
                                                    Long sentByUserId,
                                                    String fromAddress, String toAddress,
                                                    String subject, String textBody, String htmlBody,
                                                    Instant sendNotBefore,
                                                    Long appliedTemplateId, String appliedTemplateCode,
                                                    boolean contentWasEdited) {
        return enqueueScheduled(ticketId, mailboxId, null, sentByUserId,
                fromAddress, toAddress, toAddress,
                subject, textBody, htmlBody, null, null,
                sendNotBefore, appliedTemplateId, appliedTemplateCode, contentWasEdited);
    }

    private String generateMessageId() {
        return "<" + UUID.randomUUID() + "@caseflow>";
    }
}
