package com.caseflow.integration.domain;

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
import java.util.UUID;

/**
 * Durable record of a single external integration action.
 *
 * <p>Workers claim rows with PESSIMISTIC_WRITE + SKIP LOCKED, execute the action,
 * and then mark the row SUCCEEDED or FAILED. Retries are scheduled via
 * {@code nextAttemptAt}. {@code idempotencyKey} prevents duplicate jobs for
 * the same logical action.
 */
@Entity
@Table(name = "integration_jobs")
public class IntegrationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "integration_type", nullable = false, length = 100)
    private IntegrationType integrationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IntegrationJobStatus status = IntegrationJobStatus.PENDING;

    // ── Ticket / entity context ───────────────────────────────────────────────

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "ticket_public_id")
    private UUID ticketPublicId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "mailbox_id")
    private Long mailboxId;

    // ── Job payload ───────────────────────────────────────────────────────────

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /** Result reference from the external system (e.g. Jira issue key). */
    @Column(name = "external_reference", length = 500)
    private String externalReference;

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Column(name = "idempotency_key", nullable = false, length = 500, unique = true)
    private String idempotencyKey;

    // ── Retry ─────────────────────────────────────────────────────────────────

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 3;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    // ── Actor / trigger ───────────────────────────────────────────────────────

    /** USER, SYSTEM, SCHEDULE, or EVENT */
    @Column(name = "triggered_by_type", nullable = false, length = 50)
    private String triggeredByType = "USER";

    @Column(name = "created_by")
    private Long createdBy;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (nextAttemptAt == null) nextAttemptAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public IntegrationType getIntegrationType() { return integrationType; }
    public void setIntegrationType(IntegrationType integrationType) { this.integrationType = integrationType; }

    public IntegrationJobStatus getStatus() { return status; }
    public void setStatus(IntegrationJobStatus status) { this.status = status; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public UUID getTicketPublicId() { return ticketPublicId; }
    public void setTicketPublicId(UUID ticketPublicId) { this.ticketPublicId = ticketPublicId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getMailboxId() { return mailboxId; }
    public void setMailboxId(Long mailboxId) { this.mailboxId = mailboxId; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    public Integer getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getTriggeredByType() { return triggeredByType; }
    public void setTriggeredByType(String triggeredByType) { this.triggeredByType = triggeredByType; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public Instant getCanceledAt() { return canceledAt; }
    public void setCanceledAt(Instant canceledAt) { this.canceledAt = canceledAt; }
}
