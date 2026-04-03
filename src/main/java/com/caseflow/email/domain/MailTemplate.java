package com.caseflow.email.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Operator-managed email template stored in the database.
 *
 * <p>Template bodies support simple placeholder substitution:
 * <ul>
 *   <li>{@code {replyBody}} — the agent's reply text (HTML or plain)</li>
 *   <li>{@code {ticketRef}} — the ticket's public reference number</li>
 *   <li>{@code {mailboxName}} — the sending mailbox's display name</li>
 *   <li>{@code {agentName}} — the sending agent's display name</li>
 *   <li>{@code {signatureBlock}} — optional agent signature</li>
 * </ul>
 *
 * <p>Built-in templates ({@code isBuiltIn=true}) cannot be deleted.
 * They are seeded by the V17 migration and represent the default
 * structural templates for each outbound email type.
 */
@Entity
@Table(name = "mail_templates")
public class MailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Template code used for lookup (e.g. {@code CUSTOMER_REPLY}).
     * Unique — one active DB template per logical template type.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    /** Optional subject line template. Supports {ticketRef} substitution. */
    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;

    @Column(name = "html_template", nullable = false, columnDefinition = "TEXT")
    private String htmlTemplate;

    @Column(name = "plain_text_template", nullable = false, columnDefinition = "TEXT")
    private String plainTextTemplate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /** Built-in templates are seeded by migration and cannot be deleted via API. */
    @Column(name = "is_built_in", nullable = false)
    private Boolean isBuiltIn = false;

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

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }

    public String getHtmlTemplate() { return htmlTemplate; }
    public void setHtmlTemplate(String htmlTemplate) { this.htmlTemplate = htmlTemplate; }

    public String getPlainTextTemplate() { return plainTextTemplate; }
    public void setPlainTextTemplate(String plainTextTemplate) { this.plainTextTemplate = plainTextTemplate; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getIsBuiltIn() { return isBuiltIn; }
    public void setIsBuiltIn(Boolean isBuiltIn) { this.isBuiltIn = isBuiltIn; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
