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
        dispatch.setStatus(DispatchStatus.PENDING);
        OutboundEmailDispatch saved = dispatchRepository.save(dispatch);
        log.info("SMTP_SEND dispatch enqueued — dispatchId: {}, to: '{}', resolvedTo: '{}', mailboxId: {}, ticketId: {}",
                saved.getId(), toAddress, resolvedToAddress, mailboxId, ticketId);
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

    private String generateMessageId() {
        return "<" + UUID.randomUUID() + "@caseflow>";
    }
}
