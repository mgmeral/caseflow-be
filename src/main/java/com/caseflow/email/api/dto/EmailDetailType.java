package com.caseflow.email.api.dto;

/**
 * Explicit type discriminator for the unified ticket email detail endpoint.
 *
 * <p>FE should read {@code detailType} from the thread item and pass it to
 * {@code GET /api/tickets/{ticketPublicId}/email/detail/{detailType}/{detailId}}
 * rather than guessing the correct backend source.
 */
public enum EmailDetailType {
    /** Detail backed by an {@code EmailDocument} (MongoDB) — inbound emails. */
    EMAIL_DOCUMENT,
    /** Detail backed by an {@code OutboundEmailDispatch} (PostgreSQL) — outbound replies. */
    OUTBOUND_DISPATCH
}
