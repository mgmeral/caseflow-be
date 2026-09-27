package com.caseflow.integration.domain;

/**
 * Identifies which external system and action an {@link IntegrationJob} represents.
 */
public enum IntegrationType {
    JIRA_ISSUE_CREATE,
    SLACK_NOTIFICATION,
    TEAMS_NOTIFICATION,
    /** Index or un-index a ticket in caseflow-ai-service's similar-case search (AI-001). */
    AI_TICKET_SYNC,
    /** Index or un-index a knowledge-base document for AI policy guidance (AI-001). */
    AI_DOCUMENT_SYNC
}
