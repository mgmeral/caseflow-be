package com.caseflow.common.exception;

public class UserProfileException extends RuntimeException {

    private final String code;

    public UserProfileException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
