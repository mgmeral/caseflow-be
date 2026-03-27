package com.caseflow.common.exception;

import com.caseflow.ticket.domain.TicketStatus;

public class InvalidTicketStateException extends RuntimeException {

    public InvalidTicketStateException(TicketStatus from, TicketStatus to) {
        super("Invalid ticket status transition: " + from + " -> " + to);
    }
}
