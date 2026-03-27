package com.caseflow.common.exception;

public class AttachmentNotFoundException extends RuntimeException {

    public AttachmentNotFoundException(String objectKey) {
        super("Attachment not found: " + objectKey);
    }
}
