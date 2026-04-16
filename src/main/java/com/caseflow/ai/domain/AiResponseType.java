package com.caseflow.ai.domain;

/**
 * Identifies which AI operation produced a cached response in {@code ticket_ai_response_cache}.
 */
public enum AiResponseType {
    SUMMARY,
    REPLY_DRAFT,
    SIMILAR_CASES,
    POLICY_GUIDANCE
}
