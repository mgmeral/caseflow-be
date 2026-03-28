package com.caseflow.email.scheduler;

import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.email.service.EmailIngressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduled worker that drives Stage-2 processing for RECEIVED and FAILED ingress events.
 * Uses SKIP LOCKED via the repository to safely run on multiple instances.
 */
@Component
public class EmailIngressRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailIngressRetryScheduler.class);
    private static final int BATCH_SIZE = 20;

    private final EmailIngressEventRepository eventRepository;
    private final EmailIngressService ingressService;
    private final int maxAttempts;

    public EmailIngressRetryScheduler(EmailIngressEventRepository eventRepository,
                                       EmailIngressService ingressService,
                                       @Value("${caseflow.email.ingress.max-attempts:5}") int maxAttempts) {
        this.eventRepository = eventRepository;
        this.ingressService = ingressService;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${caseflow.email.ingress.retry-interval-ms:30000}")
    @Transactional
    public void processReceivedEvents() {
        List<EmailIngressEvent> events = eventRepository.findAndLockReceived(BATCH_SIZE);
        if (!events.isEmpty()) {
            log.info("Processing {} RECEIVED ingress events", events.size());
        }
        for (EmailIngressEvent event : events) {
            try {
                ingressService.processEvent(event.getId());
            } catch (Exception e) {
                log.error("Unhandled error processing ingress event {} — {}", event.getId(), e.getMessage(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${caseflow.email.ingress.retry-interval-ms:30000}",
               initialDelayString = "${caseflow.email.ingress.retry-interval-ms:30000}")
    @Transactional
    public void retryFailedEvents() {
        List<EmailIngressEvent> events = eventRepository.findAndLockFailedForRetry(maxAttempts, BATCH_SIZE);
        if (!events.isEmpty()) {
            log.info("Retrying {} FAILED ingress events (maxAttempts: {})", events.size(), maxAttempts);
        }
        for (EmailIngressEvent event : events) {
            try {
                ingressService.processEvent(event.getId());
            } catch (Exception e) {
                log.error("Retry failed for ingress event {} — {}", event.getId(), e.getMessage(), e);
            }
        }
    }
}
