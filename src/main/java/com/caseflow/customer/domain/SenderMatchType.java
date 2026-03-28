package com.caseflow.customer.domain;

public enum SenderMatchType {
    /** Match the exact sender email address (case-insensitive). */
    EXACT_EMAIL,
    /** Match any sender from this domain (e.g. "@acme.com"). */
    DOMAIN
}
