package com.caseflow.common.exception;

public class ReassignTargetUserInactiveException extends RuntimeException {

    public ReassignTargetUserInactiveException(Long userId) {
        super("Reassign target user is inactive: " + userId);
    }
}
