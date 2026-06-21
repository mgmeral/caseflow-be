package com.caseflow.common.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldViolation> details,
        String correlationId
) {
    public static ErrorResponse of(int status, String error, String code,
                                   String message, String path) {
        return new ErrorResponse(
                Instant.now(), status, error, code, message, path,
                null, resolveCorrelationId()
        );
    }

    public static ErrorResponse withDetails(int status, String error, String code,
                                            String message, String path,
                                            List<FieldViolation> details) {
        return new ErrorResponse(
                Instant.now(), status, error, code, message, path,
                details, resolveCorrelationId()
        );
    }

    private static String resolveCorrelationId() {
        String fromMdc = org.slf4j.MDC.get("correlationId");
        return fromMdc != null ? fromMdc : UUID.randomUUID().toString();
    }
}
