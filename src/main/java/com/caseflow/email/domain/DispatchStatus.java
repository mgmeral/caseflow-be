package com.caseflow.email.domain;

public enum DispatchStatus {
    /** Queued, not yet attempted. */
    PENDING,
    /** Send attempt in progress. */
    SENDING,
    /** Successfully delivered to SMTP server. */
    SENT,
    /** Last attempt failed; eligible for retry. */
    FAILED,
    /** Max attempts exhausted — no further retries. */
    PERMANENTLY_FAILED
}
