package com.caseflow.automation.domain;

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
 * Automation rule scaffold.
 *
 * <p>Rules are stored but NOT yet executed. The rule engine will evaluate rules whose
 * {@code isActive = true} when the matching {@link AutomationTriggerType} event fires.
 *
 * <h2>Condition / action JSON format</h2>
 * <pre>
 * conditionJson: [{"field": "priority", "op": "eq", "value": "HIGH"}]
 * actionJson:    [{"type": "ASSIGN_GROUP", "groupId": 5},
 *                 {"type": "SET_STATUS",   "status": "IN_PROGRESS"}]
 * </pre>
 * Schema is intentionally loose (TEXT column) to allow the engine to evolve without
 * migrations. Validation is the engine's responsibility.
 */
@Entity
@Table(name = "automation_rules")
public class AutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private AutomationTriggerType triggerType;

    /**
     * JSON array of condition objects. Null or empty array = rule fires on all events
     * of the matching trigger type.
     */
    @Column(name = "condition_json", columnDefinition = "TEXT")
    private String conditionJson;

    /**
     * JSON array of action objects. At least one action is required when the rule is active.
     */
    @Column(name = "action_json", columnDefinition = "TEXT")
    private String actionJson;

    /** Whether this rule is evaluated. Defaults to false — must be explicitly activated. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    /** Execution order when multiple rules share the same trigger. Lower = earlier. */
    @Column(name = "execution_order", nullable = false)
    private int executionOrder = 100;

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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public AutomationTriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(AutomationTriggerType triggerType) { this.triggerType = triggerType; }

    public String getConditionJson() { return conditionJson; }
    public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }

    public String getActionJson() { return actionJson; }
    public void setActionJson(String actionJson) { this.actionJson = actionJson; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
