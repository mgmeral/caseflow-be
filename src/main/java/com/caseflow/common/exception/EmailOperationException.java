package com.caseflow.common.exception;

/**
 * Thrown when a reply, schedule, or template operation cannot proceed due to a
 * semantic / business constraint. Carries a structured {@code code} that
 * {@code GlobalExceptionHandler} maps to the correct HTTP status and error body,
 * giving the frontend a machine-readable reason rather than a generic 400.
 *
 * <h2>Defined codes</h2>
 * <ul>
 *   <li>{@code SOURCE_EVENT_NOT_FOUND} → 404</li>
 *   <li>{@code SOURCE_EVENT_NOT_FOR_TICKET} → 422</li>
 *   <li>{@code REPLY_TARGET_UNRESOLVABLE} → 422</li>
 *   <li>{@code MAILBOX_NOT_FOUND} → 404</li>
 *   <li>{@code MAILBOX_NOT_ACTIVE} → 422</li>
 *   <li>{@code TEMPLATE_NOT_FOUND} → 404</li>
 *   <li>{@code TEMPLATE_INACTIVE} → 422</li>
 *   <li>{@code SCHEDULE_TIME_INVALID} → 422</li>
 *   <li>{@code REPLY_BODY_EMPTY} → 422</li>
 * </ul>
 */
public class EmailOperationException extends RuntimeException {

    private final String code;

    public EmailOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Machine-readable error code, e.g. {@code SOURCE_EVENT_NOT_FOR_TICKET}. */
    public String getCode() {
        return code;
    }
}
