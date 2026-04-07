package com.caseflow.integration.jira.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Global Jira integration configuration.
 *
 * <p>Phase 2 supports one active config per CaseFlow instance.
 * The {@code apiToken} is stored as plaintext and must never be
 * returned raw in API responses (mask to "****").
 */
@Entity
@Table(name = "jira_configs")
public class JiraConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = Boolean.FALSE;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseUrl;

    @Column(name = "auth_type", nullable = false, length = 50)
    private String authType = "BASIC";

    @Column(name = "username", length = 500)
    private String username;

    /** Never expose raw in API responses. */
    @Column(name = "api_token", columnDefinition = "TEXT")
    private String apiToken;

    @Column(name = "project_key", nullable = false, length = 100)
    private String projectKey;

    @Column(name = "issue_type", nullable = false, length = 100)
    private String issueType = "Task";

    /** Comma-separated default labels applied to every created issue. */
    @Column(name = "default_labels", columnDefinition = "TEXT")
    private String defaultLabels;

    /** Used to build back-links to tickets in Jira issue descriptions. */
    @Column(name = "app_base_url", length = 1000)
    private String appBaseUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getDefaultLabels() { return defaultLabels; }
    public void setDefaultLabels(String defaultLabels) { this.defaultLabels = defaultLabels; }

    public String getAppBaseUrl() { return appBaseUrl; }
    public void setAppBaseUrl(String appBaseUrl) { this.appBaseUrl = appBaseUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
