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
        String requestId
) {
    public static ErrorResponse of(int status, String error, String code,
                                   String message, String path) {
        return new ErrorResponse(
                Instant.now(), status, error, code, message, path,
                null, UUID.randomUUID().toString()
        );
    }

    public static ErrorResponse withDetails(int status, String error, String code,
                                            String message, String path,
                                            List<FieldViolation> details) {
        return new ErrorResponse(
                Instant.now(), status, error, code, message, path,
                details, UUID.randomUUID().toString()
        );
    }
}
