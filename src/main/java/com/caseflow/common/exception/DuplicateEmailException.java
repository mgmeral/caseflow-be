package com.caseflow.common.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String messageId) {
        super("Email with messageId already exists: " + messageId);
    }
}
