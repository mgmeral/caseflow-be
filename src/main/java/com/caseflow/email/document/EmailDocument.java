package com.caseflow.email.document;

import com.caseflow.email.domain.EmailDirection;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "emails")
public class EmailDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String messageId;

    @Indexed
    private String threadKey;

    private String inReplyTo;

    private List<String> references;

    private String subject;

    private String normalizedSubject;

    private String from;

    private List<String> to;

    private List<String> cc;

    private List<String> bcc;

    private String htmlBody;

    private String textBody;

    private List<AttachmentMetadata> attachments;

    private Instant receivedAt;

    private Instant parsedAt;

    @Indexed
    private Long ticketId;

    private EmailDirection direction;

    @Indexed
    private Long mailboxId;

    @Indexed
    private Long customerId;

    private String providerEventId;

    /** First 500 chars of textBody — used in list views without loading full body. */
    private String bodyPreview;

    public String getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getThreadKey() {
        return threadKey;
    }

    public void setThreadKey(String threadKey) {
        this.threadKey = threadKey;
    }

    public String getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(String inReplyTo) {
        this.inReplyTo = inReplyTo;
    }

    public List<String> getReferences() {
        return references;
    }

    public void setReferences(List<String> references) {
        this.references = references;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getNormalizedSubject() {
        return normalizedSubject;
    }

    public void setNormalizedSubject(String normalizedSubject) {
        this.normalizedSubject = normalizedSubject;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<String> getTo() {
        return to;
    }

    public void setTo(List<String> to) {
        this.to = to;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public List<String> getBcc() {
        return bcc;
    }

    public void setBcc(List<String> bcc) {
        this.bcc = bcc;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public List<AttachmentMetadata> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentMetadata> attachments) {
        this.attachments = attachments;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(Instant parsedAt) {
        this.parsedAt = parsedAt;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public EmailDirection getDirection() { return direction; }
    public void setDirection(EmailDirection direction) { this.direction = direction; }

    public Long getMailboxId() { return mailboxId; }
    public void setMailboxId(Long mailboxId) { this.mailboxId = mailboxId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getProviderEventId() { return providerEventId; }
    public void setProviderEventId(String providerEventId) { this.providerEventId = providerEventId; }

    public String getBodyPreview() { return bodyPreview; }
    public void setBodyPreview(String bodyPreview) { this.bodyPreview = bodyPreview; }

    public static class AttachmentMetadata {

        private String fileName;
        private String objectKey;
        private String contentType;
        private Long size;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getObjectKey() {
            return objectKey;
        }

        public void setObjectKey(String objectKey) {
            this.objectKey = objectKey;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }
    }
}
