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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Durable record of a single inbound email attempt.
 *
 * <p>Stage 1 ({@code receiveEvent}) stores the raw event immediately for durability.
 * Stage 2 ({@code processEvent}) processes it through threading, routing, and ticket creation.
 *
 * <p>Threading fields ({@code inReplyTo}, {@code rawReferences}) are persisted so Stage-2
 * can correctly link reply emails to existing tickets without re-fetching the original message.
 *
 * <p>Reply-target fields ({@code rawReplyTo}) ensure outbound replies go to the correct address.
 */
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

    /** Value of the In-Reply-To header, used for email thread resolution. */
    @Column(name = "in_reply_to", length = 512)
    private String inReplyTo;

    /**
     * Pipe-separated References header values, used for thread resolution fallback.
     * Use {@link #getReferencesList()} for structured access.
     */
    @Column(name = "raw_references", columnDefinition = "TEXT")
    private String rawReferences;

    /** Value of the Reply-To header — primary reply target for outbound replies. */
    @Column(name = "raw_reply_to", length = 512)
    private String rawReplyTo;

    @Column(name = "raw_cc", columnDefinition = "TEXT")
    private String rawCc;

    @Column(name = "raw_subject", columnDefinition = "TEXT")
    private String rawSubject;

    @Column(name = "text_body", columnDefinition = "TEXT")
    private String textBody;

    @Column(name = "html_body", columnDefinition = "TEXT")
    private String htmlBody;

    /** The actual SMTP envelope recipient — may differ from the To: header. */
    @Column(name = "envelope_recipient", length = 512)
    private String envelopeRecipient;

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

    /** Returns References header values as a list. Never null. */
    public List<String> getReferencesList() {
        if (rawReferences == null || rawReferences.isBlank()) return Collections.emptyList();
        return Arrays.stream(rawReferences.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Returns the best reply-to address for outbound replies.
     * Uses Reply-To header if present, falls back to From.
     */
    public String effectiveReplyTo() {
        return (rawReplyTo != null && !rawReplyTo.isBlank()) ? rawReplyTo : rawFrom;
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

    public String getInReplyTo() { return inReplyTo; }
    public void setInReplyTo(String inReplyTo) { this.inReplyTo = inReplyTo; }

    public String getRawReferences() { return rawReferences; }
    public void setRawReferences(String rawReferences) { this.rawReferences = rawReferences; }

    public String getRawReplyTo() { return rawReplyTo; }
    public void setRawReplyTo(String rawReplyTo) { this.rawReplyTo = rawReplyTo; }

    public String getRawCc() { return rawCc; }
    public void setRawCc(String rawCc) { this.rawCc = rawCc; }

    public String getRawSubject() { return rawSubject; }
    public void setRawSubject(String rawSubject) { this.rawSubject = rawSubject; }

    public String getTextBody() { return textBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }

    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }

    public String getEnvelopeRecipient() { return envelopeRecipient; }
    public void setEnvelopeRecipient(String envelopeRecipient) { this.envelopeRecipient = envelopeRecipient; }

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
