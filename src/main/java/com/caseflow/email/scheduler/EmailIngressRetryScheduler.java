package com.caseflow.email.scheduler;

import com.caseflow.email.service.EmailIngressService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled worker that drives Stage-2 processing for RECEIVED and FAILED ingress events.
 *
 * <h3>Transaction design</h3>
 * <p>The scheduler itself is intentionally <em>not</em> {@code @Transactional}. Each run is
 * split into two steps:
 * <ol>
 *   <li><b>Claim</b> — {@link EmailIngressService#claimReceivedBatch} /
 *       {@link EmailIngressService#claimFailedBatch}: runs in its own {@code @Transactional}
 *       method inside the service. It acquires a PESSIMISTIC_WRITE + SKIP LOCKED lock on
 *       eligible rows, marks them {@code PROCESSING}, then <em>commits</em>. Committing releases
 *       the row lock before any event is processed.</li>
 *   <li><b>Process</b> — {@link EmailIngressService#processEvent}: each call runs in
 *       {@code REQUIRES_NEW}. Because the claim transaction has already committed, there is
 *       no outer lock to deadlock against. A failure on one event's transaction does not affect
 *       the others.</li>
 * </ol>
 *
 * <p>This avoids two problems that appeared in earlier designs:
 * <ul>
 *   <li><b>TransactionRequiredException</b>: the PESSIMISTIC_WRITE lock query requires an active
 *       transaction. Calling the repository directly from a non-transactional scheduler method
 *       fails at runtime.</li>
 *   <li><b>Deadlock</b>: wrapping the entire loop in {@code @Transactional} holds the row lock
 *       while {@code processEvent}'s {@code REQUIRES_NEW} tries to UPDATE those same rows.</li>
 * </ul>
 */
@Component
public class EmailIngressRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailIngressRetryScheduler.class);
    private static final int BATCH_SIZE = 20;

    private final EmailIngressService ingressService;
    private final int maxAttempts;

    public EmailIngressRetryScheduler(EmailIngressService ingressService,
                                       @Value("${caseflow.email.ingress.max-attempts:5}") int maxAttempts) {
        this.ingressService = ingressService;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Claims and processes RECEIVED events. The claim step runs in a short committed
     * transaction; each processEvent call runs in its own independent transaction.
     */
    @Scheduled(fixedDelayString = "${caseflow.email.ingress.retry-interval-ms:30000}")
    public void processReceivedEvents() {
        List<Long> ids = ingressService.claimReceivedBatch(BATCH_SIZE);
        if (!ids.isEmpty()) {
            log.info("Processing {} claimed RECEIVED events", ids.size());
        }
        for (Long id : ids) {
            try {
                ingressService.processEvent(id);
            } catch (Exception e) {
                log.error("Unhandled error processing ingress event {} — {}", id, e.getMessage(), e);
            }
        }
    }

    /** Same isolation rationale as {@link #processReceivedEvents()}. */
    @Scheduled(fixedDelayString = "${caseflow.email.ingress.retry-interval-ms:30000}",
               initialDelayString = "${caseflow.email.ingress.retry-interval-ms:30000}")
    public void retryFailedEvents() {
        List<Long> ids = ingressService.claimFailedBatch(maxAttempts, BATCH_SIZE);
        if (!ids.isEmpty()) {
            log.info("Retrying {} claimed FAILED events (maxAttempts: {})", ids.size(), maxAttempts);
        }
        for (Long id : ids) {
            try {
                ingressService.processEvent(id);
            } catch (Exception e) {
                log.error("Retry failed for ingress event {} — {}", id, e.getMessage(), e);
            }
        }
    }
}
