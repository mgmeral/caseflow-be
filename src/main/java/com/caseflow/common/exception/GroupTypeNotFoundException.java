package com.caseflow.common.exception;

public class GroupTypeNotFoundException extends RuntimeException {

    public GroupTypeNotFoundException(Long groupTypeId) {
        super("GroupType not found: " + groupTypeId);
    }
}
