package com.caseflow.common.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("User not found: " + userId);
    }
}
