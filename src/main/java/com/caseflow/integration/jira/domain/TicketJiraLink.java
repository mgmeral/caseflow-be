package com.caseflow.integration.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable link between a CaseFlow ticket and a Jira issue.
 *
 * <p>Created by the Jira job processor after a successful issue creation.
 * The unique constraint on {@code ticketId} ensures at most one active link per ticket.
 */
@Entity
@Table(name = "ticket_jira_links")
public class TicketJiraLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    @Column(name = "ticket_public_id", nullable = false)
    private UUID ticketPublicId;

    @Column(name = "jira_issue_key", nullable = false, length = 200)
    private String jiraIssueKey;

    @Column(name = "jira_issue_id", length = 200)
    private String jiraIssueId;

    @Column(name = "jira_url", nullable = false, length = 2000)
    private String jiraUrl;

    @Column(name = "integration_job_id")
    private Long integrationJobId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public UUID getTicketPublicId() { return ticketPublicId; }
    public void setTicketPublicId(UUID ticketPublicId) { this.ticketPublicId = ticketPublicId; }

    public String getJiraIssueKey() { return jiraIssueKey; }
    public void setJiraIssueKey(String jiraIssueKey) { this.jiraIssueKey = jiraIssueKey; }

    public String getJiraIssueId() { return jiraIssueId; }
    public void setJiraIssueId(String jiraIssueId) { this.jiraIssueId = jiraIssueId; }

    public String getJiraUrl() { return jiraUrl; }
    public void setJiraUrl(String jiraUrl) { this.jiraUrl = jiraUrl; }

    public Long getIntegrationJobId() { return integrationJobId; }
    public void setIntegrationJobId(Long integrationJobId) { this.integrationJobId = integrationJobId; }

    public Instant getCreatedAt() { return createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
