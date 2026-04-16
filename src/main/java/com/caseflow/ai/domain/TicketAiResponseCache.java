package com.caseflow.ai.domain;

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
 * Caches AI responses by ticket, source version, and response type.
 *
 * <p>A cache entry is valid when:
 * <ol>
 *   <li>Its {@code sourceVersion} matches the current {@link TicketAiIndex#getSourceVersion()}</li>
 *   <li>Its {@code expiresAt} has not passed</li>
 *   <li>{@code isStale} is false</li>
 * </ol>
 *
 * <p>When the ticket source version changes (new email, note, tag), cache entries become stale
 * and are invalidated by {@link com.caseflow.ai.service.AiSourceVersionService}.
 */
@Entity
@Table(name = "ticket_ai_response_cache")
public class TicketAiResponseCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, length = 30)
    private AiResponseType responseType;

    /** Serialized JSON of the BE response DTO. */
    @Column(name = "response_payload", nullable = false, columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_stale", nullable = false)
    private boolean isStale = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isValid() {
        return !isStale && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }

    public void invalidate() {
        this.isStale = true;
    }

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(long sourceVersion) { this.sourceVersion = sourceVersion; }

    public AiResponseType getResponseType() { return responseType; }
    public void setResponseType(AiResponseType responseType) { this.responseType = responseType; }

    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isStale() { return isStale; }
    public void setStale(boolean stale) { isStale = stale; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
