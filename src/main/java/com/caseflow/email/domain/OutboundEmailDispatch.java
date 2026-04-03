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
@Table(name = "outbound_email_dispatches")
public class OutboundEmailDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    /** The mailbox used to dispatch this email. Drives SMTP configuration selection. */
    @Column(name = "mailbox_id")
    private Long mailboxId;

    /**
     * The inbound {@code EmailIngressEvent} this reply is responding to.
     * Used for reply-target derivation and timeline correlation.
     */
    @Column(name = "source_ingress_event_id")
    private Long sourceIngressEventId;

    /** The agent user who triggered this outbound reply. Null for system-generated emails. */
    @Column(name = "sent_by_user_id")
    private Long sentByUserId;

    @Column(name = "message_id", nullable = false, unique = true, length = 512)
    private String messageId;

    @Column(name = "from_address", nullable = false, length = 512)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 512)
    private String toAddress;

    /**
     * The reply-to address resolved from the source inbound email headers
     * (replyTo → from). Stored for audit; may differ from {@code toAddress}
     * if the FE provided an override.
     */
    @Column(name = "resolved_to_address", length = 512)
    private String resolvedToAddress;

    @Column(name = "subject", nullable = false, columnDefinition = "TEXT")
    private String subject;

    @Column(name = "text_body", columnDefinition = "TEXT")
    private String textBody;

    @Column(name = "html_body", columnDefinition = "TEXT")
    private String htmlBody;

    @Column(name = "in_reply_to_message_id", length = 512)
    private String inReplyToMessageId;

    /** Full RFC 2822 References chain (space-separated message-ids). */
    @Column(name = "references_header", length = 2048)
    private String referencesHeader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DispatchStatus status = DispatchStatus.PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", length = 100)
    private DispatchFailureCategory failureCategory;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
        if (scheduledAt == null) scheduledAt = createdAt;
    }

    public Long getId() { return id; }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public Long getMailboxId() { return mailboxId; }
    public void setMailboxId(Long mailboxId) { this.mailboxId = mailboxId; }

    public Long getSourceIngressEventId() { return sourceIngressEventId; }
    public void setSourceIngressEventId(Long sourceIngressEventId) {
        this.sourceIngressEventId = sourceIngressEventId;
    }

    public Long getSentByUserId() { return sentByUserId; }
    public void setSentByUserId(Long sentByUserId) { this.sentByUserId = sentByUserId; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public String getResolvedToAddress() { return resolvedToAddress; }
    public void setResolvedToAddress(String resolvedToAddress) {
        this.resolvedToAddress = resolvedToAddress;
    }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTextBody() { return textBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }

    public String getHtmlBody() { return htmlBody; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }

    public String getInReplyToMessageId() { return inReplyToMessageId; }
    public void setInReplyToMessageId(String inReplyToMessageId) {
        this.inReplyToMessageId = inReplyToMessageId;
    }

    public String getReferencesHeader() { return referencesHeader; }
    public void setReferencesHeader(String referencesHeader) { this.referencesHeader = referencesHeader; }

    public DispatchStatus getStatus() { return status; }
    public void setStatus(DispatchStatus status) { this.status = status; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public DispatchFailureCategory getFailureCategory() { return failureCategory; }
    public void setFailureCategory(DispatchFailureCategory failureCategory) {
        this.failureCategory = failureCategory;
    }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public Instant getCreatedAt() { return createdAt; }
}
