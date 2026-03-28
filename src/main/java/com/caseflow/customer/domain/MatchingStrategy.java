package com.caseflow.customer.domain;

/**
 * Determines the precedence order used when routing inbound emails to customers.
 */
public enum MatchingStrategy {
    /** Check explicit routing rules first, fall back to contact email match. */
    RULES_FIRST,
    /** Check contact email match first, fall back to routing rules. */
    CONTACT_FIRST
}
