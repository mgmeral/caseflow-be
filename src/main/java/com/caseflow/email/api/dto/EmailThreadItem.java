package com.caseflow.email.api.dto;

import java.time.Instant;

/**
 * Unified view of a single item in a ticket's email thread.
 * Combines inbound events and outbound dispatches into a chronological timeline.
 *
 * <h2>FE detail fetch contract</h2>
 * Use {@code detailType} + {@code detailId} to fetch the full detail view from:
 * {@code GET /api/tickets/{ticketPublicId}/email/detail/{detailType}/{detailId}}
 * Do NOT guess the detail source from {@code direction} alone.
 */
public record EmailThreadItem(
        /** "INBOUND" or "OUTBOUND" */
        String direction,

        /** Internal numeric id (ingressEventId for INBOUND, dispatchId for OUTBOUND). */
        Long id,

        String messageId,
        String fromAddress,
        String toAddress,
        String subject,

        /** String form of IngressEventStatus (INBOUND) or DispatchStatus (OUTBOUND). */
        String status,

        /** receivedAt for INBOUND, sentAt or createdAt for OUTBOUND. */
        Instant timestamp,

        /** First ~500 chars of email body — null if body is unavailable or not yet processed.
         *  INBOUND: from EmailDocument.bodyPreview; OUTBOUND: from OutboundEmailDispatch.textBody. */
        String bodyPreview,

        /** Number of attachments on this email. */
        int attachmentCount,

        // ── Phase 1 additions — all fields below are additive ─────────────────

        /** MongoDB EmailDocument id for INBOUND items. Null for OUTBOUND. */
        String emailDocumentId,

        /** The ingressEventId that sourced this item (same as id for INBOUND, or the linked
         *  sourceIngressEventId for an OUTBOUND reply). */
        Long sourceEventId,

        /** Mailbox that received (INBOUND) or sent (OUTBOUND) this email. */
        Long mailboxId,

        /** Display name of that mailbox. */
        String mailboxName,

        /** Failure reason if status is FAILED or PERMANENTLY_FAILED (OUTBOUND).
         *  Null when not applicable or no failure. */
        String failureReason,

        /** Backend-derived reply target address — populated for OUTBOUND dispatches. */
        String resolvedReplyTarget,

        /** Explicit type discriminator — tells FE how to fetch the full detail.
         *  Always present. Values: EMAIL_DOCUMENT, OUTBOUND_DISPATCH. */
        String detailType,

        /** The canonical id to pass to the unified detail endpoint.
         *  For EMAIL_DOCUMENT: the MongoDB document id (String).
         *  For OUTBOUND_DISPATCH: the dispatch id as String. */
        String detailId,

        /** True when attachments are present (convenience flag). */
        boolean hasAttachments,

        /** True when a body preview is available (non-null, non-blank). */
        boolean isPreviewAvailable
) {}

