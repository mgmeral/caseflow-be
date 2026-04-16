package com.caseflow.ai.client;

/**
 * Thrown when the AI service is unreachable, times out, or returns a non-retryable error.
 * Callers must handle this and return a graceful fallback — never propagate to ticket workflows.
 */
public class AiServiceUnavailableException extends RuntimeException {

    private final String operation;
    private final int httpStatus;

    public AiServiceUnavailableException(String operation, String message) {
        super(message);
        this.operation = operation;
        this.httpStatus = 0;
    }

    public AiServiceUnavailableException(String operation, int httpStatus, String message) {
        super(message);
        this.operation = operation;
        this.httpStatus = httpStatus;
    }

    public AiServiceUnavailableException(String operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.httpStatus = 0;
    }

    public String getOperation() { return operation; }
    public int getHttpStatus() { return httpStatus; }
}
