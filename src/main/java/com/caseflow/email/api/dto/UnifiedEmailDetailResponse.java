package com.caseflow.email.api.dto;

import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Normalized email detail response returned by the unified ticket-scoped detail endpoint.
 *
 * <p>Covers both inbound email documents and outbound dispatches through a single contract.
 * FE reads {@code detailType} to understand which direction this email flows.
 *
 * <h2>Detail fetch</h2>
 * Fetch via: {@code GET /api/tickets/{ticketPublicId}/email/detail/{detailType}/{detailId}}
 * where {@code detailType} and {@code detailId} come from the thread item.
 */
public record UnifiedEmailDetailResponse(

        /** EMAIL_DOCUMENT or OUTBOUND_DISPATCH */
        String detailType,

        /** The canonical id (emailDocumentId for INBOUND, dispatchId string for OUTBOUND). */
        String id,

        UUID ticketPublicId,

        /** INBOUND or OUTBOUND */
        String direction,

        Long mailboxId,
        String mailboxName,
        String mailboxAddress,

        String messageId,

        /** The In-Reply-To message-id header value. */
        String threadMessageId,

        String fromAddress,
        String toAddress,
        List<String> cc,
        List<String> bcc,
        String replyTo,
        String subject,

        /** Current dispatch status (OUTBOUND) or ingress event status (INBOUND). */
        String status,

        /** Failure description if status is FAILED or PERMANENTLY_FAILED. */
        String failureReason,

        /** When the outbound dispatch was sent, or null if not yet sent. */
        Instant sentAt,

        /** When the inbound email was received by the mailbox. */
        Instant receivedAt,

        Instant createdAt,

        /** Plain-text body (sanitized/as-stored). */
        String bodyText,

        /** HTML body — sanitized for INBOUND emails, raw rendered content for OUTBOUND. */
        String bodyHtml,

        /** First 500 chars — used when FE wants a quick summary without loading full body. */
        String bodyPreview,

        /** Attachments — metadata only, content via the attachment content endpoint. */
        List<AttachmentMetadataResponse> attachments,

        /** Template info if a DB template was applied (OUTBOUND only). */
        TemplateInfo templateInfo,

        /** Reply derivation context (OUTBOUND only). */
        ReplyContext replyContext,

        /** Whether the agent modified template-rendered content before sending (OUTBOUND only). */
        Boolean contentWasEdited

) {
    /** Template metadata embedded in outbound detail. */
    public record TemplateInfo(
            Long templateId,
            String templateCode,
            String templateName
    ) {}

    /** Reply context embedded in outbound detail. */
    public record ReplyContext(
            /** The backend-derived recipient address. */
            String derivedReplyTarget,
            /** INBOUND_EVENT or MANUAL_OVERRIDE. */
            String replySourceType,
            /** The source ingress event id if reply was sourced from an inbound event. */
            Long replySourceId
    ) {}
}
