package com.caseflow.email.domain;

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
@Table(name = "email_mailboxes")
public class EmailMailbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false, unique = true)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 50)
    private ProviderType providerType = ProviderType.SMTP_RELAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "inbound_mode", nullable = false, length = 50)
    private InboundMode inboundMode = InboundMode.WEBHOOK;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbound_mode", nullable = false, length = 50)
    private OutboundMode outboundMode = OutboundMode.SMTP;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "default_group_id")
    private Long defaultGroupId;

    @Column(name = "default_priority", length = 50)
    private String defaultPriority;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password", length = 512)
    private String smtpPassword;

    @Column(name = "smtp_use_ssl", nullable = false)
    private Boolean smtpUseSsl = Boolean.FALSE;

    @Column(name = "last_successful_inbound_at")
    private Instant lastSuccessfulInboundAt;

    @Column(name = "last_successful_outbound_at")
    private Instant lastSuccessfulOutboundAt;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public ProviderType getProviderType() { return providerType; }
    public void setProviderType(ProviderType providerType) { this.providerType = providerType; }

    public InboundMode getInboundMode() { return inboundMode; }
    public void setInboundMode(InboundMode inboundMode) { this.inboundMode = inboundMode; }

    public OutboundMode getOutboundMode() { return outboundMode; }
    public void setOutboundMode(OutboundMode outboundMode) { this.outboundMode = outboundMode; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Long getDefaultGroupId() { return defaultGroupId; }
    public void setDefaultGroupId(Long defaultGroupId) { this.defaultGroupId = defaultGroupId; }

    public String getDefaultPriority() { return defaultPriority; }
    public void setDefaultPriority(String defaultPriority) { this.defaultPriority = defaultPriority; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }

    public Boolean getSmtpUseSsl() { return smtpUseSsl; }
    public void setSmtpUseSsl(Boolean smtpUseSsl) { this.smtpUseSsl = smtpUseSsl; }

    public Instant getLastSuccessfulInboundAt() { return lastSuccessfulInboundAt; }
    public void setLastSuccessfulInboundAt(Instant lastSuccessfulInboundAt) {
        this.lastSuccessfulInboundAt = lastSuccessfulInboundAt;
    }

    public Instant getLastSuccessfulOutboundAt() { return lastSuccessfulOutboundAt; }
    public void setLastSuccessfulOutboundAt(Instant lastSuccessfulOutboundAt) {
        this.lastSuccessfulOutboundAt = lastSuccessfulOutboundAt;
    }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
