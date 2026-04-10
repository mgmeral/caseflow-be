package com.caseflow.common.exception;

public class ReassignTargetUserNotFoundException extends RuntimeException {

    public ReassignTargetUserNotFoundException(Long userId) {
        super("Reassign target user not found: " + userId);
    }
}
