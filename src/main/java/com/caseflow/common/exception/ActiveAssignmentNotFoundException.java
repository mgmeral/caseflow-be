package com.caseflow.common.exception;

public class ActiveAssignmentNotFoundException extends RuntimeException {

    public ActiveAssignmentNotFoundException(Long ticketId) {
        super("No active assignment found for ticket: " + ticketId);
    }
}
