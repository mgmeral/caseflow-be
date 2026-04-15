package com.caseflow.common.exception;

import com.caseflow.email.domain.IngressEventStatus;

public class InvalidIngressEventStateException extends RuntimeException {

    public InvalidIngressEventStateException(Long eventId, String operation,
                                              IngressEventStatus actual, IngressEventStatus... expected) {
        super(buildMessage(eventId, operation, actual, expected));
    }

    private static String buildMessage(Long eventId, String operation,
                                        IngressEventStatus actual, IngressEventStatus[] expected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event ").append(eventId).append(" cannot be ").append(operation)
          .append(": status is ").append(actual);
        if (expected.length == 1) {
            sb.append(" (expected ").append(expected[0]).append(")");
        } else if (expected.length > 1) {
            sb.append(" (expected one of: ");
            for (int i = 0; i < expected.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(expected[i]);
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
