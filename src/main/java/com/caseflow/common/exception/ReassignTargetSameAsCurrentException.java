package com.caseflow.common.exception;

public class ReassignTargetSameAsCurrentException extends RuntimeException {

    public ReassignTargetSameAsCurrentException(Long ticketId) {
        super("Ticket is already assigned to the selected user/group: " + ticketId);
    }
}
