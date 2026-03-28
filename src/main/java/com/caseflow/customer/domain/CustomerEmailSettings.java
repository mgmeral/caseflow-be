package com.caseflow.customer.domain;

import com.caseflow.common.domain.UnknownSenderPolicy;
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

@Entity
@Table(name = "customer_email_settings")
public class CustomerEmailSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unknown_sender_policy", nullable = false, length = 50)
    private UnknownSenderPolicy unknownSenderPolicy = UnknownSenderPolicy.MANUAL_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "matching_strategy", nullable = false, length = 50)
    private MatchingStrategy matchingStrategy = MatchingStrategy.CONTACT_FIRST;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public UnknownSenderPolicy getUnknownSenderPolicy() { return unknownSenderPolicy; }
    public void setUnknownSenderPolicy(UnknownSenderPolicy unknownSenderPolicy) {
        this.unknownSenderPolicy = unknownSenderPolicy;
    }

    public MatchingStrategy getMatchingStrategy() { return matchingStrategy; }
    public void setMatchingStrategy(MatchingStrategy matchingStrategy) {
        this.matchingStrategy = matchingStrategy;
    }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
