package com.caseflow.ticket.domain;

public enum AttachmentSourceType {
    /** Uploaded directly via the API attachment endpoint. */
    UPLOAD,
    /** Extracted from an inbound email. */
    EMAIL_INBOUND,
    /** Attached to an outbound customer reply. */
    EMAIL_OUTBOUND
}
