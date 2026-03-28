package com.caseflow.email.domain;

public enum IngressEventStatus {
    /** Event stored; not yet processed. */
    RECEIVED,
    /** Currently being processed by Stage-2 worker. */
    PROCESSING,
    /** Processing completed successfully. */
    PROCESSED,
    /** Processing failed; eligible for retry. */
    FAILED,
    /** Permanently held for human review (e.g. unknown sender, loop detected). */
    QUARANTINED
}
