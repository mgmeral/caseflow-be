package com.caseflow.security.audit;

public enum SecurityAuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED,
    TOKEN_THEFT_DETECTED,
    SESSION_LIMIT_ENFORCED,
    PASSWORD_CHANGED,
    ROLE_CHANGED
}
