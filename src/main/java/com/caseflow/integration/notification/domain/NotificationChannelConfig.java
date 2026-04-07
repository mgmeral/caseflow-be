package com.caseflow.integration.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Configuration for an external webhook notification channel (Slack or Teams).
 *
 * <p>{@code webhookUrl} is stored as plaintext and must never be returned raw in
 * read API responses — mask it to "****".
 *
 * <p>{@code subscribedEvents} is a JSON array of {@link NotificationEventType} names.
 * {@code scopeType} controls which tickets trigger this channel:
 * <ul>
 *   <li>GLOBAL — all tickets</li>
 *   <li>GROUP — only tickets assigned to the group at {@code scopeId}</li>
 *   <li>CUSTOMER — only tickets belonging to the customer at {@code scopeId}</li>
 * </ul>
 */
@Entity
@Table(name = "notification_channel_configs")
public class NotificationChannelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 50)
    private ChannelType channelType;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = Boolean.TRUE;

    /** Webhook URL. Never expose raw in API responses. */
    @Column(name = "webhook_url", nullable = false, columnDefinition = "TEXT")
    private String webhookUrl;

    /** JSON array, e.g. ["TICKET_CREATED","TICKET_RESOLVED"] */
    @Column(name = "subscribed_events", nullable = false, columnDefinition = "TEXT")
    private String subscribedEvents = "[]";

    @Column(name = "scope_type", nullable = false, length = 50)
    private String scopeType = "GLOBAL";

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ChannelType getChannelType() { return channelType; }
    public void setChannelType(ChannelType channelType) { this.channelType = channelType; }

    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }

    public String getSubscribedEvents() { return subscribedEvents; }
    public void setSubscribedEvents(String subscribedEvents) { this.subscribedEvents = subscribedEvents; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
