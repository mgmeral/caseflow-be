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
 * Tracks AI indexing/sync state for a ticket.
 *
 * <p>One row per ticket. {@code sourceVersion} is incremented by
 * {@link com.caseflow.ai.service.AiSourceVersionService} when AI-relevant ticket data changes.
 * When {@code sourceVersion > indexedVersion} the row is considered stale.
 *
 * <p>Populated via {@link com.caseflow.ai.orchestration.AiIngestOrchestrator}.
 */
@Entity
@Table(name = "ticket_ai_index")
public class TicketAiIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private Long ticketId;

    /** Incremented each time AI-relevant ticket data changes (emails, notes, tags, etc.). */
    @Column(name = "source_version", nullable = false)
    private long sourceVersion = 1L;

    /** The sourceVersion that was last successfully indexed by the AI service. */
    @Column(name = "indexed_version", nullable = false)
    private long indexedVersion = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private AiSyncStatus syncStatus = AiSyncStatus.PENDING;

    @Column(name = "last_requested_at")
    private Instant lastRequestedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "consecutive_failure_count", nullable = false)
    private int consecutiveFailureCount = 0;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_event_id", length = 100)
    private String lastEventId;

    @Column(name = "last_correlation_id", length = 100)
    private String lastCorrelationId;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    /** Schema version of the index format — allows backward-incompatible schema changes. */
    @Column(name = "index_schema_version", nullable = false)
    private int indexSchemaVersion = 1;

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

    // ── Convenience state helpers ─────────────────────────────────────────────

    /** Returns true when the indexed content is behind the current source version. */
    public boolean isStale() {
        return sourceVersion > indexedVersion;
    }

    public void markStale() {
        this.syncStatus = AiSyncStatus.STALE;
    }

    public void markPending(String eventId, String correlationId) {
        this.syncStatus = AiSyncStatus.PENDING;
        this.lastRequestedAt = Instant.now();
        this.lastEventId = eventId;
        this.lastCorrelationId = correlationId;
    }

    public void markProcessing() {
        this.syncStatus = AiSyncStatus.PROCESSING;
        this.processingStartedAt = Instant.now();
    }

    public void markSynced(long indexedVersion, String embeddingModel) {
        this.syncStatus = AiSyncStatus.SYNCED;
        this.indexedVersion = indexedVersion;
        this.lastSyncedAt = Instant.now();
        this.consecutiveFailureCount = 0;
        this.embeddingModel = embeddingModel;
        this.lastError = null;
        this.lastErrorCode = null;
        this.lastErrorAt = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.syncStatus = AiSyncStatus.FAILED;
        this.consecutiveFailureCount++;
        this.lastErrorCode = errorCode;
        this.lastError = errorMessage;
        this.lastErrorAt = Instant.now();
    }

    public void markSkipped() {
        this.syncStatus = AiSyncStatus.SKIPPED;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public long getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(long sourceVersion) { this.sourceVersion = sourceVersion; }

    public long getIndexedVersion() { return indexedVersion; }
    public void setIndexedVersion(long indexedVersion) { this.indexedVersion = indexedVersion; }

    public AiSyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(AiSyncStatus syncStatus) { this.syncStatus = syncStatus; }

    public Instant getLastRequestedAt() { return lastRequestedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }

    public int getConsecutiveFailureCount() { return consecutiveFailureCount; }

    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastError() { return lastError; }
    public Instant getLastErrorAt() { return lastErrorAt; }

    public String getLastEventId() { return lastEventId; }
    public String getLastCorrelationId() { return lastCorrelationId; }

    public String getEmbeddingModel() { return embeddingModel; }
    public int getIndexSchemaVersion() { return indexSchemaVersion; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
