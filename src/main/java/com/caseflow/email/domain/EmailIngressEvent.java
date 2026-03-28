package com.caseflow.email.domain;

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

@Entity
@Table(name = "email_ingress_events")
public class EmailIngressEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mailbox_id")
    private Long mailboxId;

    @Column(name = "message_id", nullable = false, length = 512)
    private String messageId;

    @Column(name = "raw_from", nullable = false, length = 512)
    private String rawFrom;

    @Column(name = "raw_to", columnDefinition = "TEXT")
    private String rawTo;

    @Column(name = "raw_subject", columnDefinition = "TEXT")
    private String rawSubject;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IngressEventStatus status = IngressEventStatus.RECEIVED;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "processing_attempts", nullable = false)
    private Integer processingAttempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "document_id", length = 255)
    private String documentId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @PrePersist
    private void onCreate() {
        if (receivedAt == null) receivedAt = Instant.now();
    }

    public Long getId() { return id; }

    public Long getMailboxId() { return mailboxId; }
    public void setMailboxId(Long mailboxId) { this.mailboxId = mailboxId; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getRawFrom() { return rawFrom; }
    public void setRawFrom(String rawFrom) { this.rawFrom = rawFrom; }

    public String getRawTo() { return rawTo; }
    public void setRawTo(String rawTo) { this.rawTo = rawTo; }

    public String getRawSubject() { return rawSubject; }
    public void setRawSubject(String rawSubject) { this.rawSubject = rawSubject; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public IngressEventStatus getStatus() { return status; }
    public void setStatus(IngressEventStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Integer getProcessingAttempts() { return processingAttempts; }
    public void setProcessingAttempts(Integer processingAttempts) { this.processingAttempts = processingAttempts; }

    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
}
