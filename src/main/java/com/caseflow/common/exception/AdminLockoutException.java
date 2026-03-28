package com.caseflow.common.exception;

public class AdminLockoutException extends RuntimeException {

    public AdminLockoutException(String message) {
        super(message);
    }
}
