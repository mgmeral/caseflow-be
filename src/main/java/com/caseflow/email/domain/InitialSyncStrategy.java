package com.caseflow.email.domain;

/**
 * Controls what happens on the very first IMAP poll for a mailbox (when {@code lastSeenUid} is null).
 *
 * <p><b>START_FROM_LATEST</b> (default, production-safe): advance the cursor to the current highest
 * UID in the folder without ingesting any historical messages.  Subsequent polls will only ingest
 * messages that arrive after the mailbox was onboarded.
 *
 * <p><b>BACKFILL_ALL</b>: start from UID 1 and ingest the entire inbox history.  Must be set
 * intentionally by an operator.  Suitable for initial setup of low-volume mailboxes where historical
 * messages must be imported into CaseFlow.
 */
public enum InitialSyncStrategy {
    START_FROM_LATEST,
    BACKFILL_ALL
}
