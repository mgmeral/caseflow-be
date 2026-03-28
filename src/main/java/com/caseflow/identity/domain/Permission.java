package com.caseflow.identity.domain;

/**
 * Static permission catalog — owned by the backend, never stored as an entity.
 * Permission codes are stored as strings in the role_permissions table.
 */
public enum Permission {
    USER_MANAGE,
    ROLE_MANAGE,
    GROUP_MANAGE,
    TICKET_READ,
    ADMIN_POOL_VIEW,
    TICKET_ASSIGN,
    TICKET_TRANSFER,
    TICKET_STATUS_CHANGE,
    TICKET_CLOSE,
    TICKET_PRIORITY_CHANGE,
    CUSTOMER_REPLY_SEND,
    INTERNAL_NOTE_ADD,
    REPORT_VIEW,
    DATA_EXPORT
}
