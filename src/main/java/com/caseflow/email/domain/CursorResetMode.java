package com.caseflow.email.domain;

/**
 * Controls how the IMAP UID cursor is reset for a mailbox.
 *
 * @see com.caseflow.email.service.EmailMailboxService#resetCursor
 */
public enum CursorResetMode {

    /**
     * Clear the cursor without changing the initial sync strategy.
     * Next poll re-enters the first-time onboarding path using the mailbox's existing
     * {@code initialSyncStrategy}.
     */
    CLEAR_FOR_REINIT,

    /**
     * Clear the cursor and set {@code initialSyncStrategy = NEW_MESSAGES_ONLY}.
     * Next poll advances the cursor to the current inbox top without ingesting
     * historical messages.
     */
    SET_TO_LATEST,

    /**
     * Set the cursor to an explicit UID value.
     * Next poll processes messages with UID &gt; the provided value.
     * Requires {@code explicitUid} in the request body.
     */
    SET_EXPLICIT_UID
}
