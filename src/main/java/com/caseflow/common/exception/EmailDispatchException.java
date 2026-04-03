package com.caseflow.common.exception;

import com.caseflow.email.domain.DispatchFailureCategory;

public class EmailDispatchException extends RuntimeException {

    private final DispatchFailureCategory category;

    public EmailDispatchException(String message) {
        super(message);
        this.category = DispatchFailureCategory.UNKNOWN;
    }

    public EmailDispatchException(String message, DispatchFailureCategory category) {
        super(message);
        this.category = category;
    }

    public EmailDispatchException(String message, Throwable cause) {
        super(message, cause);
        this.category = DispatchFailureCategory.UNKNOWN;
    }

    public EmailDispatchException(String message, DispatchFailureCategory category, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public DispatchFailureCategory getCategory() {
        return category;
    }
}
