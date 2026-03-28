package com.caseflow.email.domain;

public enum InboundMode {
    /** CaseFlow polls the mailbox on a schedule. */
    POLLING,
    /** An external service pushes parsed events to the ingest endpoint. */
    WEBHOOK
}
