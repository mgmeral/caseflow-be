package com.caseflow.common.exception;

public class MailboxNotFoundException extends RuntimeException {

    public MailboxNotFoundException(Long id) {
        super("Mailbox not found: " + id);
    }

    public MailboxNotFoundException(String address) {
        super("Mailbox not found for address: " + address);
    }
}
