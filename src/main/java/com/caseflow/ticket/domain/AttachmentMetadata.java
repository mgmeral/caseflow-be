package com.caseflow.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment_metadata")
public class AttachmentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    /**
     * Denormalised copy of {@code tickets.public_id} for use in stable object key paths.
     * Set when the ticket is finalised after routing; may be null for staging-stage records.
     */
    @Column(name = "ticket_public_id")
    private UUID ticketPublicId;

    @Column(name = "email_id")
    private String emailId;

    /** References {@code email_ingress_events.id} for attachments ingested via IMAP. */
    @Column(name = "ingress_event_id")
    private Long ingressEventId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private AttachmentSourceType sourceType = AttachmentSourceType.UPLOAD;

    /**
     * STAGING: stored under mailbox-scoped staging prefix before ticket is assigned.
     * FINAL: stored under stable ticket/email prefix after routing completes.
     */
    @Column(name = "storage_stage", nullable = false, length = 50)
    private String storageStage = "FINAL";

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    private void onCreate() {
        uploadedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public UUID getTicketPublicId() { return ticketPublicId; }
    public void setTicketPublicId(UUID ticketPublicId) { this.ticketPublicId = ticketPublicId; }

    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public Long getIngressEventId() { return ingressEventId; }
    public void setIngressEventId(Long ingressEventId) { this.ingressEventId = ingressEventId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public AttachmentSourceType getSourceType() { return sourceType; }
    public void setSourceType(AttachmentSourceType sourceType) { this.sourceType = sourceType; }

    public String getStorageStage() { return storageStage; }
    public void setStorageStage(String storageStage) { this.storageStage = storageStage; }

    public Instant getUploadedAt() { return uploadedAt; }
}
