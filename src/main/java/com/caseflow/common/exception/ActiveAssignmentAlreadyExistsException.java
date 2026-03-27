package com.caseflow.common.exception;

public class ActiveAssignmentAlreadyExistsException extends RuntimeException {

    public ActiveAssignmentAlreadyExistsException(Long ticketId) {
        super("Ticket " + ticketId + " already has an active assignment. Use reassign instead.");
    }
}
