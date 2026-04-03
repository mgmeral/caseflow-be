package com.caseflow.email.domain;

/**
 * Controls what happens on the very first IMAP poll for a mailbox (when {@code lastSeenUid} is null).
 *
 * <p><b>NEW_MESSAGES_ONLY</b> (default, production-safe): advance the cursor to the current highest
 * UID in the folder without ingesting any historical messages. Subsequent polls only ingest messages
 * that arrive after the mailbox was onboarded.
 *
 * <p><b>SCAN_FROM_START</b>: start from UID 1 and ingest the entire inbox history.
 * Must be set intentionally by an operator.
 *
 * <p><b>SCAN_LAST_1_DAY / SCAN_LAST_3_DAYS / SCAN_LAST_7_DAYS</b>: ingest only messages received
 * within the last N days, then advance the cursor to the current highest UID. Uses IMAP SEARCH
 * where possible; falls back to date-filtering fetched candidates.
 *
 * <p>DB migration V16 renames legacy values:
 * {@code START_FROM_LATEST} → {@code NEW_MESSAGES_ONLY},
 * {@code BACKFILL_ALL} → {@code SCAN_FROM_START}.
 */
public enum InitialSyncStrategy {
    /** (Default) Advance cursor to latest UID without ingesting history. */
    NEW_MESSAGES_ONLY,
    /** Ingest entire inbox from UID 1. Explicit operator opt-in only. */
    SCAN_FROM_START,
    /** Ingest messages received in the last 1 day, then advance cursor. */
    SCAN_LAST_1_DAY,
    /** Ingest messages received in the last 3 days, then advance cursor. */
    SCAN_LAST_3_DAYS,
    /** Ingest messages received in the last 7 days, then advance cursor. */
    SCAN_LAST_7_DAYS
}
