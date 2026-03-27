package com.caseflow.common.api;

public record FieldViolation(
        String field,
        String message,
        Object rejectedValue,
        String code
) {
    public static FieldViolation of(String field, String message, Object rejectedValue, String code) {
        return new FieldViolation(field, message, rejectedValue, code);
    }
}
