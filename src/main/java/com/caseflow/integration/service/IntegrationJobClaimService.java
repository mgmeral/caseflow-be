package com.caseflow.integration.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationJobStatus;
import com.caseflow.integration.repository.IntegrationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional claim service for {@link IntegrationJob} batches.
 *
 * <p>Exists as a separate bean so that {@link com.caseflow.integration.scheduler.IntegrationJobWorker}
 * can call it through the Spring proxy, guaranteeing that the PESSIMISTIC_WRITE / SKIP LOCKED
 * queries always execute inside an active transaction. Self-invocation on the worker bean
 * bypasses the proxy and makes {@code @Transactional} a no-op, which is why this class exists.
 */
@Service
public class IntegrationJobClaimService {

    private final IntegrationJobRepository jobRepository;

    public IntegrationJobClaimService(IntegrationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Claims up to {@code limit} PENDING jobs, marks them PROCESSING, and increments
     * attempt count — all inside one transaction that commits before processing begins.
     */
    @Transactional
    public List<IntegrationJob> claimPendingBatch(int limit) {
        List<IntegrationJob> batch = jobRepository.claimPendingBatch(Instant.now(), limit);
        for (IntegrationJob job : batch) {
            job.setStatus(IntegrationJobStatus.PROCESSING);
            job.setAttemptCount(job.getAttemptCount() + 1);
        }
        return batch;
    }

    /**
     * Claims up to {@code limit} FAILED jobs eligible for retry, marks them PROCESSING,
     * and increments attempt count — all inside one transaction that commits before processing begins.
     */
    @Transactional
    public List<IntegrationJob> claimRetryBatch(int limit) {
        List<IntegrationJob> batch = jobRepository.claimRetryBatch(Instant.now(), limit);
        for (IntegrationJob job : batch) {
            job.setStatus(IntegrationJobStatus.PROCESSING);
            job.setAttemptCount(job.getAttemptCount() + 1);
        }
        return batch;
    }
}
