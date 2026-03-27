package com.caseflow.email.service;

import java.time.Instant;
import java.util.List;

public class ParsedEmail {

    private String messageId;
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
    private List<ParsedAttachment> attachments;
    private Instant receivedAt;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
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

    public List<ParsedAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<ParsedAttachment> attachments) {
        this.attachments = attachments;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public static class ParsedAttachment {

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
