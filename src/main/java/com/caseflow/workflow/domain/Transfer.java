package com.caseflow.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private Long ticketId;

    @Column(name = "from_group_id", nullable = false, updatable = false)
    private Long fromGroupId;

    @Column(name = "to_group_id", nullable = false, updatable = false)
    private Long toGroupId;

    @Column(name = "transferred_by", nullable = false, updatable = false)
    private Long transferredBy;

    @Column(name = "transferred_at", nullable = false, updatable = false)
    private Instant transferredAt;

    @Column(updatable = false)
    private String reason;

    @PrePersist
    private void onCreate() {
        transferredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getFromGroupId() {
        return fromGroupId;
    }

    public void setFromGroupId(Long fromGroupId) {
        this.fromGroupId = fromGroupId;
    }

    public Long getToGroupId() {
        return toGroupId;
    }

    public void setToGroupId(Long toGroupId) {
        this.toGroupId = toGroupId;
    }

    public Long getTransferredBy() {
        return transferredBy;
    }

    public void setTransferredBy(Long transferredBy) {
        this.transferredBy = transferredBy;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
