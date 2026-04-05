package com.caseflow.common.exception;

public class CustomerDeleteBlockedException extends RuntimeException {

    public CustomerDeleteBlockedException(Long customerId, long ticketCount) {
        super("Cannot delete customer " + customerId + ": " + ticketCount
                + " ticket(s) are linked to this customer. "
                + "Reassign or close all tickets before deleting.");
    }
}
