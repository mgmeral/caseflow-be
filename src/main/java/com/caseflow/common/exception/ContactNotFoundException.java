package com.caseflow.common.exception;

public class ContactNotFoundException extends RuntimeException {

    public ContactNotFoundException(Long contactId) {
        super("Contact not found: " + contactId);
    }
}
