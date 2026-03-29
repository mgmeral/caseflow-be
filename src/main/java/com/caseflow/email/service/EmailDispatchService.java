package com.caseflow.email.service;

import com.caseflow.common.exception.DispatchNotFoundException;
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
     */
    @Transactional
    public OutboundEmailDispatch enqueue(Long ticketId, String fromAddress, String toAddress,
                                         String subject, String textBody, String htmlBody,
                                         String inReplyToMessageId) {
        OutboundEmailDispatch dispatch = new OutboundEmailDispatch();
        dispatch.setTicketId(ticketId);
        dispatch.setMessageId(generateMessageId());
        dispatch.setFromAddress(fromAddress);
        dispatch.setToAddress(toAddress);
        dispatch.setSubject(subject);
        dispatch.setTextBody(textBody);
        dispatch.setHtmlBody(htmlBody);
        dispatch.setInReplyToMessageId(inReplyToMessageId);
        dispatch.setStatus(DispatchStatus.PENDING);
        OutboundEmailDispatch saved = dispatchRepository.save(dispatch);
        log.info("Dispatch enqueued — dispatchId: {}, to: '{}', ticketId: {}",
                saved.getId(), toAddress, ticketId);
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
    public void markFailed(OutboundEmailDispatch dispatch, String reason) {
        dispatch.setStatus(DispatchStatus.FAILED);
        dispatch.setFailureReason(reason);
        dispatchRepository.save(dispatch);
    }

    @Transactional
    public void markPermanentlyFailed(OutboundEmailDispatch dispatch, String reason) {
        dispatch.setStatus(DispatchStatus.PERMANENTLY_FAILED);
        dispatch.setFailureReason(reason);
        dispatchRepository.save(dispatch);
        log.error("Dispatch permanently failed — dispatchId: {}, reason: {}", dispatch.getId(), reason);
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
