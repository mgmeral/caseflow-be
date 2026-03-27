package com.caseflow.common.exception;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(Long ticketId) {
        super("Ticket not found: " + ticketId);
    }

    public TicketNotFoundException(String ticketNo) {
        super("Ticket not found: " + ticketNo);
    }
}
