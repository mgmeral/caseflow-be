package com.caseflow.common.exception;

public class SlaPolicyNotFoundException extends RuntimeException {
    public SlaPolicyNotFoundException(Long id) {
        super("SLA policy not found: " + id);
    }
}
