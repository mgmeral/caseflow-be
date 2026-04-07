package com.caseflow.integration.domain;

/**
 * Lifecycle status for a durable {@link IntegrationJob}.
 */
public enum IntegrationJobStatus {
    /** Queued and waiting for a worker. */
    PENDING,
    /** A worker has claimed this job and is executing it. */
    PROCESSING,
    /** External action confirmed successfully. */
    SUCCEEDED,
    /** Last attempt failed; within retry budget. */
    FAILED,
    /** Max attempts exhausted — no further retries. */
    PERMANENTLY_FAILED,
    /** Explicitly canceled by a user or system process. */
    CANCELED
}
