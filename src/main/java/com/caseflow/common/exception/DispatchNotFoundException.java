package com.caseflow.common.exception;

public class DispatchNotFoundException extends RuntimeException {

    public DispatchNotFoundException(Long id) {
        super("Outbound dispatch not found: " + id);
    }
}
