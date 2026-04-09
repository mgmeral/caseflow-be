package com.caseflow.email.service;

import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailIngressEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Operator recovery operations for inbound ingress events.
 *
 * <p>These operations allow an operator to inspect, manually trigger, quarantine,
 * or release stuck / failed events without modifying the automatic retry scheduler.
 */
@Service
public class IngressEventAdminService {

    private static final Logger log = LoggerFactory.getLogger(IngressEventAdminService.class);

    private final EmailIngressEventRepository eventRepository;
    private final EmailIngressService ingressService;

    public IngressEventAdminService(EmailIngressEventRepository eventRepository,
                                     EmailIngressService ingressService) {
        this.eventRepository = eventRepository;
        this.ingressService = ingressService;
    }

    /**
     * Lists ingress events matching the given filters. All parameters are optional.
     *
     * @param status    filter by status; null = all statuses
     * @param mailboxId filter by mailbox; null = all mailboxes
     * @param messageId exact match on RFC 5322 Message-ID; null = no filter
     * @param ticketId  filter by linked ticket; null = all events
     * @param from      filter events received on or after this time; null = no lower bound
     * @param to        filter events received on or before this time; null = no upper bound
     */
    @Transactional(readOnly = true)
    public Page<EmailIngressEvent> findFiltered(IngressEventStatus status,
                                                 Long mailboxId, String messageId, Long ticketId,
                                                 Instant from, Instant to,
                                                 Pageable pageable) {
        return eventRepository.findFiltered(status, mailboxId, messageId, ticketId, from, to, pageable);
    }

    /**
     * Loads a single event by id; throws if not found.
     */
    @Transactional(readOnly = true)
    public EmailIngressEvent getById(Long id) {
        return findOrThrow(id);
    }

    /**
     * Manually triggers Stage-2 processing for the event.
     * Works for events in RECEIVED, FAILED, or QUARANTINED state.
     * QUARANTINED events are released to RECEIVED first, then processed.
     */
    @Transactional
    public void processEvent(Long eventId) {
        EmailIngressEvent event = findOrThrow(eventId);
        log.info("ADMIN_PROCESS eventId: {}, currentStatus: {}", eventId, event.getStatus());
        if (event.getStatus() == IngressEventStatus.QUARANTINED) {
            ingressService.releaseEvent(eventId);
        }
        ingressService.processEvent(eventId);
    }

    /**
     * Resets a FAILED event to RECEIVED and triggers immediate processing.
     * Provides an instant retry without waiting for the retry scheduler.
     *
     * @throws IllegalStateException when the event is not in FAILED state
     */
    @Transactional
    public void retryEvent(Long eventId) {
        EmailIngressEvent event = findOrThrow(eventId);
        if (event.getStatus() != IngressEventStatus.FAILED) {
            throw new IllegalStateException(
                    "Event " + eventId + " cannot be retried: status is " + event.getStatus()
                            + " (expected FAILED)");
        }
        log.info("ADMIN_RETRY eventId: {}", eventId);
        ingressService.processEvent(eventId);
    }

    /**
     * Moves an event to QUARANTINED with an explicit reason.
     * Quarantined events are not picked up by the retry scheduler.
     *
     * @throws IllegalStateException when the event is already in a terminal state (PROCESSED)
     */
    @Transactional
    public void quarantineEvent(Long eventId, String reason) {
        EmailIngressEvent event = findOrThrow(eventId);
        if (event.getStatus() == IngressEventStatus.PROCESSED) {
            throw new IllegalStateException(
                    "Event " + eventId + " is already PROCESSED and cannot be quarantined");
        }
        log.info("ADMIN_QUARANTINE eventId: {}, reason: '{}'", eventId, reason);
        ingressService.quarantineEvent(eventId, reason);
    }

    /**
     * Releases a QUARANTINED event back to RECEIVED so the retry scheduler picks it up.
     *
     * @throws IllegalStateException when the event is not in QUARANTINED state
     */
    @Transactional
    public void releaseEvent(Long eventId) {
        EmailIngressEvent event = findOrThrow(eventId);
        if (event.getStatus() != IngressEventStatus.QUARANTINED) {
            throw new IllegalStateException(
                    "Event " + eventId + " cannot be released: status is " + event.getStatus()
                            + " (expected QUARANTINED)");
        }
        log.info("ADMIN_RELEASE eventId: {}", eventId);
        ingressService.releaseEvent(eventId);
    }

    private EmailIngressEvent findOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ingress event not found: " + id));
    }
}
