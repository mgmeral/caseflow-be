package com.caseflow.integration.scheduler;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;
import com.caseflow.integration.service.IntegrationJobClaimService;
import com.caseflow.integration.service.IntegrationJobExecutionException;
import com.caseflow.integration.service.IntegrationJobProcessor;
import com.caseflow.integration.service.IntegrationJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scheduled worker that processes pending and failed {@link IntegrationJob} records.
 *
 * <p>Discovers all {@link IntegrationJobProcessor} beans and routes each job to the
 * matching processor. Uses PESSIMISTIC_WRITE + SKIP LOCKED for safe multi-instance operation.
 *
 * <p>Transaction boundaries:
 * <ol>
 *   <li>Claim batch ({@link IntegrationJobClaimService}) — marks rows PROCESSING, commits.
 *       Claim is delegated to a separate Spring bean so the call goes through the proxy
 *       and {@code @Transactional} is honoured (self-invocation would bypass it).</li>
 *   <li>Process each job — each {@link IntegrationJobService} state-transition method
 *       is {@code @Transactional} and opens its own transaction, isolating failures.</li>
 * </ol>
 */
@Component
public class IntegrationJobWorker {

    private static final Logger log = LoggerFactory.getLogger(IntegrationJobWorker.class);
    private static final int BATCH_SIZE = 10;

    private final IntegrationJobClaimService claimService;
    private final IntegrationJobService jobService;
    private final Map<IntegrationType, IntegrationJobProcessor> processors;

    public IntegrationJobWorker(IntegrationJobClaimService claimService,
                                IntegrationJobService jobService,
                                List<IntegrationJobProcessor> processorList) {
        this.claimService = claimService;
        this.jobService = jobService;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(IntegrationJobProcessor::supportedType, Function.identity()));
        log.info("IntegrationJobWorker started with processors: {}", this.processors.keySet());
    }

    @Scheduled(fixedDelayString = "${caseflow.integration.worker.interval-ms:30000}")
    public void processPending() {
        List<IntegrationJob> batch = claimService.claimPendingBatch(BATCH_SIZE);
        if (!batch.isEmpty()) {
            log.info("INTEGRATION_WORKER claiming {} PENDING jobs", batch.size());
        }
        for (IntegrationJob job : batch) {
            processJob(job);
        }
    }

    @Scheduled(fixedDelayString = "${caseflow.integration.worker.interval-ms:30000}",
               initialDelayString = "${caseflow.integration.worker.interval-ms:30000}")
    public void retryFailed() {
        List<IntegrationJob> batch = claimService.claimRetryBatch(BATCH_SIZE);
        if (!batch.isEmpty()) {
            log.info("INTEGRATION_WORKER retrying {} FAILED jobs", batch.size());
        }
        for (IntegrationJob job : batch) {
            processJob(job);
        }
    }

    void processJob(IntegrationJob job) {
        IntegrationJobProcessor processor = processors.get(job.getIntegrationType());
        if (processor == null) {
            jobService.markPermanentlyFailed(job,
                    "No processor registered for type: " + job.getIntegrationType());
            return;
        }

        log.debug("Processing integration job {} (type: {}, attempt: {})",
                job.getId(), job.getIntegrationType(), job.getAttemptCount());
        try {
            processor.process(job);
            // Processor is responsible for calling jobService.markSucceeded(job, ref)
        } catch (IntegrationJobExecutionException e) {
            if (e.isPermanent()) {
                jobService.markPermanentlyFailed(job, e.getMessage());
            } else {
                jobService.markFailed(job, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing integration job {} — {}",
                    job.getId(), e.getMessage(), e);
            jobService.markFailed(job, "Unexpected error: " + e.getMessage());
        }
    }
}
