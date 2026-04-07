package com.caseflow.integration.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.repository.IntegrationJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the lifecycle of {@link IntegrationJob} records.
 *
 * <p>This service handles job creation (with idempotency), state transitions
 * (PROCESSING → SUCCEEDED/FAILED), and retry scheduling. Workers and processors
 * call this service to update job state — they do not mutate job fields directly.
 */
@Service
public class IntegrationJobService {

    private static final Logger log = LoggerFactory.getLogger(IntegrationJobService.class);

    /** Exponential backoff base: attempt 1 → 1 min, attempt 2 → 4 min, attempt 3 → 9 min. */
    private static final Duration BACKOFF_BASE = Duration.ofMinutes(1);

    private final IntegrationJobRepository jobRepository;

    public IntegrationJobService(IntegrationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    // ── Job creation ──────────────────────────────────────────────────────────

    /**
     * Enqueues a new integration job if no job with the given idempotency key exists.
     *
     * @return the existing job if the key already exists (duplicate-safe), or the newly created one
     */
    @Transactional
    public IntegrationJob enqueue(IntegrationType type,
                                  Long ticketId,
                                  UUID ticketPublicId,
                                  Long customerId,
                                  Long mailboxId,
                                  String payloadJson,
                                  String idempotencyKey,
                                  String triggeredByType,
                                  Long createdBy) {

        Optional<IntegrationJob> existing = jobRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Integration job already exists for idempotency key: {}", idempotencyKey);
            return existing.get();
        }

        IntegrationJob job = new IntegrationJob();
        job.setIntegrationType(type);
        job.setTicketId(ticketId);
        job.setTicketPublicId(ticketPublicId);
        job.setCustomerId(customerId);
        job.setMailboxId(mailboxId);
        job.setPayloadJson(payloadJson);
        job.setIdempotencyKey(idempotencyKey);
        job.setTriggeredByType(triggeredByType);
        job.setCreatedBy(createdBy);
        job.setStatus(IntegrationJobStatus.PENDING);

        IntegrationJob saved = jobRepository.save(job);
        log.info("Integration job enqueued — id: {}, type: {}, ticket: {}",
                saved.getId(), type, ticketId);
        return saved;
    }

    // ── State transitions ─────────────────────────────────────────────────────

    /**
     * Transitions the job to PROCESSING, incrementing the attempt count.
     * Should be called by the worker immediately before invoking the processor.
     */
    @Transactional
    public void markProcessing(IntegrationJob job) {
        job.setStatus(IntegrationJobStatus.PROCESSING);
        job.setAttemptCount(job.getAttemptCount() + 1);
        jobRepository.save(job);
    }

    /**
     * Marks the job SUCCEEDED, storing the external system reference.
     */
    @Transactional
    public void markSucceeded(IntegrationJob job, String externalReference) {
        job.setStatus(IntegrationJobStatus.SUCCEEDED);
        job.setExternalReference(externalReference);
        job.setProcessedAt(Instant.now());
        job.setLastError(null);
        jobRepository.save(job);
        log.info("Integration job {} succeeded — type: {}, externalRef: {}",
                job.getId(), job.getIntegrationType(), externalReference);
    }

    /**
     * Marks the job FAILED and schedules its next retry attempt with exponential backoff.
     * If max attempts are exhausted, marks PERMANENTLY_FAILED instead.
     */
    @Transactional
    public void markFailed(IntegrationJob job, String error) {
        job.setLastError(truncate(error, 2000));

        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            job.setStatus(IntegrationJobStatus.PERMANENTLY_FAILED);
            job.setProcessedAt(Instant.now());
            log.warn("Integration job {} permanently failed after {} attempts — type: {}",
                    job.getId(), job.getAttemptCount(), job.getIntegrationType());
        } else {
            job.setStatus(IntegrationJobStatus.FAILED);
            job.setNextAttemptAt(computeNextAttempt(job.getAttemptCount()));
            log.warn("Integration job {} failed (attempt {}/{}) — type: {}, error: {}",
                    job.getId(), job.getAttemptCount(), job.getMaxAttempts(),
                    job.getIntegrationType(), truncate(error, 200));
        }
        jobRepository.save(job);
    }

    /**
     * Marks the job PERMANENTLY_FAILED immediately, regardless of attempt count.
     * Used for non-recoverable errors (missing config, resource not found).
     */
    @Transactional
    public void markPermanentlyFailed(IntegrationJob job, String error) {
        job.setStatus(IntegrationJobStatus.PERMANENTLY_FAILED);
        job.setLastError(truncate(error, 2000));
        job.setProcessedAt(Instant.now());
        jobRepository.save(job);
        log.warn("Integration job {} permanently failed (non-recoverable) — type: {}, error: {}",
                job.getId(), job.getIntegrationType(), truncate(error, 200));
    }

    /**
     * Transitions a FAILED job back to PENDING for an immediate retry.
     * Used for manual retry requests.
     */
    @Transactional
    public void resetForRetry(IntegrationJob job) {
        if (job.getStatus() != IntegrationJobStatus.FAILED
                && job.getStatus() != IntegrationJobStatus.PERMANENTLY_FAILED) {
            throw new IllegalStateException(
                    "Cannot retry job " + job.getId() + " in status " + job.getStatus());
        }
        job.setStatus(IntegrationJobStatus.PENDING);
        job.setNextAttemptAt(Instant.now());
        job.setLastError(null);
        jobRepository.save(job);
        log.info("Integration job {} reset for retry", job.getId());
    }

    /**
     * Cancels a PENDING job. No-op if already terminal.
     */
    @Transactional
    public void cancel(IntegrationJob job) {
        if (job.getStatus() == IntegrationJobStatus.SUCCEEDED
                || job.getStatus() == IntegrationJobStatus.CANCELED) {
            return;
        }
        job.setStatus(IntegrationJobStatus.CANCELED);
        job.setCanceledAt(Instant.now());
        jobRepository.save(job);
        log.info("Integration job {} canceled", job.getId());
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean hasActiveJob(Long ticketId, IntegrationType type) {
        return jobRepository.countActiveByTicketAndType(ticketId, type) > 0;
    }

    @Transactional(readOnly = true)
    public Optional<IntegrationJob> findByIdempotencyKey(String key) {
        return jobRepository.findByIdempotencyKey(key);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Instant computeNextAttempt(int attemptCount) {
        // Exponential: attempt n → (n^2) * BASE
        long seconds = (long) attemptCount * attemptCount * BACKOFF_BASE.toSeconds();
        return Instant.now().plusSeconds(Math.min(seconds, 3600)); // cap at 1 hour
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
