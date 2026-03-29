package com.caseflow.common.exception;

/**
 * Thrown when a mailbox configuration violates invariants required for safe operation.
 * Examples: polling enabled without IMAP credentials, WEBHOOK provider with polling enabled.
 */
public class InvalidMailboxConfigException extends RuntimeException {

    public InvalidMailboxConfigException(String message) {
        super(message);
    }
}
