package com.caseflow.integration.service;

/**
 * Thrown by an {@link IntegrationJobProcessor} when job execution fails.
 *
 * <p>Set {@code permanent = true} when the failure cannot be resolved by retrying
 * (e.g. invalid configuration, resource not found in the external system).
 */
public class IntegrationJobExecutionException extends RuntimeException {

    private final boolean permanent;

    public IntegrationJobExecutionException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public IntegrationJobExecutionException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean isPermanent() { return permanent; }
}
