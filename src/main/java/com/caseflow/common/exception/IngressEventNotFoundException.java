package com.caseflow.common.exception;

public class IngressEventNotFoundException extends RuntimeException {

    public IngressEventNotFoundException(Long id) {
        super("Ingress event not found: " + id);
    }
}
