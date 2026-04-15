package com.caseflow.common.exception;

public class IngressEventNotFoundException extends RuntimeException {

    public IngressEventNotFoundException(Long eventId) {
        super("Ingress event not found: " + eventId);
    }
}
