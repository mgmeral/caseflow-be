package com.caseflow.ticket.domain;

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
import jakarta.persistence.Version;
import com.caseflow.ticket.domain.TicketChannel;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Stable external-facing UUID — safe for use in attachment storage paths and external APIs.
     * Numeric {@code id} remains the internal DB PK. {@code ticketNo} remains the human identifier.
     */
    @Column(name = "public_id", nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @Column(name = "ticket_no", nullable = false, unique = true)
    private String ticketNo;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketPriority priority;

    @Column(name = "assigned_user_id")
    private Long assignedUserId;

    @Column(name = "assigned_group_id")
    private Long assignedGroupId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private TicketChannel channel = TicketChannel.EMAIL;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /**
     * When the current {@link TicketStatus} was entered.
     * Updated whenever {@link #setStatus(TicketStatus)} is called.
     * NULL for tickets created before V28 migration.
     * Semantics: "how long has this ticket been in its current state?"
     */
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    // ── SLA tracking ──────────────────────────────────────────────────────────

    /**
     * When the first-response SLA target expires for this ticket.
     * Stamped at ticket creation from the applicable SLA policy.
     * Null when no SLA policy is configured.
     */
    @Column(name = "first_response_due_at")
    private Instant firstResponseDueAt;

    /**
     * When the resolution SLA target expires for this ticket.
     * Stamped at ticket creation from the applicable SLA policy.
     * Null when no SLA policy is configured.
     */
    @Column(name = "resolution_due_at")
    private Instant resolutionDueAt;

    /**
     * When the first customer-visible outbound reply was confirmed sent (SMTP success).
     * Set by {@link com.caseflow.email.scheduler.OutboundDispatchScheduler} on the first
     * successfully dispatched outbound email for this ticket.
     * Null until the first reply is sent.
     */
    @Column(name = "first_response_responded_at")
    private Instant firstResponseRespondedAt;

    /**
     * When the ticket reached RESOLVED status.
     * Set by the state machine when a RESOLVED transition occurs.
     * Null for open or CLOSED tickets.
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    private void onCreate() {
        if (publicId == null) publicId = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
        statusChangedAt = Instant.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        if (this.status != status) {
            this.statusChangedAt = Instant.now();
        }
        this.status = status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(Long assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public Long getAssignedGroupId() {
        return assignedGroupId;
    }

    public void setAssignedGroupId(Long assignedGroupId) {
        this.assignedGroupId = assignedGroupId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getFirstResponseDueAt() { return firstResponseDueAt; }
    public void setFirstResponseDueAt(Instant firstResponseDueAt) {
        this.firstResponseDueAt = firstResponseDueAt;
    }

    public Instant getResolutionDueAt() { return resolutionDueAt; }
    public void setResolutionDueAt(Instant resolutionDueAt) {
        this.resolutionDueAt = resolutionDueAt;
    }

    public Instant getFirstResponseRespondedAt() { return firstResponseRespondedAt; }
    public void setFirstResponseRespondedAt(Instant firstResponseRespondedAt) {
        this.firstResponseRespondedAt = firstResponseRespondedAt;
    }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public TicketChannel getChannel() { return channel; }
    public void setChannel(TicketChannel channel) { this.channel = channel; }

    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }

    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    // ── Merge/split ───────────────────────────────────────────────────────────

    /** Non-null when this ticket has been merged into another ticket. */
    @Column(name = "parent_ticket_id")
    private Long parentTicketId;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "merged_by")
    private Long mergedBy;

    public Long getParentTicketId() { return parentTicketId; }
    public void setParentTicketId(Long parentTicketId) { this.parentTicketId = parentTicketId; }

    public Instant getMergedAt() { return mergedAt; }
    public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }

    public Long getMergedBy() { return mergedBy; }
    public void setMergedBy(Long mergedBy) { this.mergedBy = mergedBy; }
}
