package com.caseflow.common.exception;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException(String identifier) {
        super("Duplicate email: " + identifier);
    }
}
