package com.caseflow.ai.domain;

/**
 * Tracks the AI indexing state of a ticket in the {@code ticket_ai_index} table.
 *
 * <ul>
 *   <li>{@link #PENDING} — sync has been requested, not yet started</li>
 *   <li>{@link #PROCESSING} — actively being indexed by the AI service</li>
 *   <li>{@link #SYNCED} — successfully indexed, sourceVersion == indexedVersion</li>
 *   <li>{@link #FAILED} — last sync attempt failed, see lastError</li>
 *   <li>{@link #STALE} — sourceVersion > indexedVersion, re-sync needed</li>
 *   <li>{@link #SKIPPED} — explicitly excluded from indexing (e.g. Kafka disabled)</li>
 * </ul>
 */
public enum AiSyncStatus {
    PENDING,
    PROCESSING,
    SYNCED,
    FAILED,
    STALE,
    SKIPPED
}
