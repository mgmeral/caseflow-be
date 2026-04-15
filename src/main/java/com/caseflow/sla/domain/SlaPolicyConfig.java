package com.caseflow.sla.domain;

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
 * Configurable SLA policy.
 *
 * <p>Resolution priority (highest to lowest):
 * <ol>
 *   <li>PRIORITY scope matching ticket's priority</li>
 *   <li>GROUP scope matching ticket's assigned group (reserved)</li>
 *   <li>CUSTOMER scope matching ticket's customer (reserved)</li>
 *   <li>GLOBAL default (fallback)</li>
 * </ol>
 *
 * <p>Only one active GLOBAL policy is used. Multiple PRIORITY policies may exist,
 * one per priority level.
 */
@Entity
@Table(name = "sla_policy_configs")
public class SlaPolicyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SlaScope scope = SlaScope.GLOBAL;

    /** Non-null only when scope = PRIORITY. Stores the TicketPriority name. */
    @Column(length = 50)
    private String priority;

    /** Non-null only when scope = GROUP. */
    @Column(name = "group_id")
    private Long groupId;

    /** Minutes until first-response SLA target is due. */
    @Column(name = "first_response_target_minutes", nullable = false)
    private int firstResponseTargetMinutes = 60;

    /** Minutes until resolution SLA target is due. */
    @Column(name = "resolution_target_minutes", nullable = false)
    private int resolutionTargetMinutes = 480;

    /** Minutes before due time at which to emit a WARNING event. */
    @Column(name = "warning_before_breach_minutes", nullable = false)
    private int warningBeforeBreachMinutes = 15;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

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

    public SlaScope getScope() { return scope; }
    public void setScope(SlaScope scope) { this.scope = scope; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public int getFirstResponseTargetMinutes() { return firstResponseTargetMinutes; }
    public void setFirstResponseTargetMinutes(int firstResponseTargetMinutes) {
        this.firstResponseTargetMinutes = firstResponseTargetMinutes;
    }

    public int getResolutionTargetMinutes() { return resolutionTargetMinutes; }
    public void setResolutionTargetMinutes(int resolutionTargetMinutes) {
        this.resolutionTargetMinutes = resolutionTargetMinutes;
    }

    public int getWarningBeforeBreachMinutes() { return warningBeforeBreachMinutes; }
    public void setWarningBeforeBreachMinutes(int warningBeforeBreachMinutes) {
        this.warningBeforeBreachMinutes = warningBeforeBreachMinutes;
    }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
