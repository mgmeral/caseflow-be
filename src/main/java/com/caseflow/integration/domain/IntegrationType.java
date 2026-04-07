package com.caseflow.integration.domain;

/**
 * Identifies which external system and action an {@link IntegrationJob} represents.
 */
public enum IntegrationType {
    JIRA_ISSUE_CREATE,
    SLACK_NOTIFICATION,
    TEAMS_NOTIFICATION
}
