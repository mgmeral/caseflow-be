package com.caseflow.customer.domain;

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
@Table(name = "customer_email_routing_rules")
public class CustomerEmailRoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_match_type", nullable = false, length = 50)
    private SenderMatchType senderMatchType;

    @Column(name = "match_value", nullable = false, length = 512)
    private String matchValue;

    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    /**
     * When {@code true} and {@code senderMatchType == DOMAIN}, the rule also matches
     * sub-domains of {@code matchValue} (e.g. rule {@code bigcorp.com} also matches
     * {@code mail.bigcorp.com}). Ignored for EXACT_EMAIL rules.
     */
    @Column(name = "allow_subdomains", nullable = false)
    private Boolean allowSubdomains = Boolean.FALSE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public SenderMatchType getSenderMatchType() { return senderMatchType; }
    public void setSenderMatchType(SenderMatchType senderMatchType) {
        this.senderMatchType = senderMatchType;
    }

    public String getMatchValue() { return matchValue; }
    public void setMatchValue(String matchValue) { this.matchValue = matchValue; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getAllowSubdomains() { return allowSubdomains; }
    public void setAllowSubdomains(Boolean allowSubdomains) { this.allowSubdomains = allowSubdomains; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
