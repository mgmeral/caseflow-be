-- V16: P0 incident hardening.
--
-- A. Rename InitialSyncStrategy enum values to clearer production-safe names:
--    START_FROM_LATEST -> NEW_MESSAGES_ONLY  (safe default — no history ingestion)
--    BACKFILL_ALL      -> SCAN_FROM_START    (explicit operator opt-in — full history)
-- B. No schema changes needed for pre-routing guard, attachment gating,
--    reply transition timing, or SMTP test — those are code-only fixes.

-- ── A. Rename InitialSyncStrategy stored values ───────────────────────────────
UPDATE email_mailboxes
SET initial_sync_strategy = 'NEW_MESSAGES_ONLY'
WHERE initial_sync_strategy = 'START_FROM_LATEST';

UPDATE email_mailboxes
SET initial_sync_strategy = 'SCAN_FROM_START'
WHERE initial_sync_strategy = 'BACKFILL_ALL';
