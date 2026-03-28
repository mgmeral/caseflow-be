package com.caseflow.common.exception;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(Long roleId) {
        super("Role not found: " + roleId);
    }
}
