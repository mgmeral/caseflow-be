package com.caseflow.integration.service;

import com.caseflow.integration.domain.IntegrationJob;
import com.caseflow.integration.domain.IntegrationType;

/**
 * Strategy interface for executing a specific type of integration job.
 *
 * <p>Implementations are discovered by {@link com.caseflow.integration.scheduler.IntegrationJobWorker}
 * and invoked for their supported {@link IntegrationType}. Each processor is responsible
 * for calling external APIs and recording history events on success/failure.
 *
 * <p>Implementations must NOT commit or roll back the current transaction — the worker
 * manages the transaction boundary.
 */
public interface IntegrationJobProcessor {

    IntegrationType supportedType();

    /**
     * Execute the integration job synchronously.
     *
     * <p>On success, the processor should update the job's {@code externalReference}
     * and any associated domain records (e.g. {@link com.caseflow.integration.jira.domain.TicketJiraLink}).
     *
     * <p>On failure, throw a {@link IntegrationJobExecutionException} with the error details.
     * The worker will handle status transitions and retry scheduling.
     *
     * @throws IntegrationJobExecutionException on recoverable or permanent failure
     */
    void process(IntegrationJob job) throws IntegrationJobExecutionException;
}
