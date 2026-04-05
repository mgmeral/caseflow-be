package com.caseflow.email.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/tickets/{ticketPublicId}/email/reply/preview.
 *
 * <p>The backend derives the reply target, renders the selected template,
 * merges any provided overrides, and returns a fully rendered preview.
 * The FE may show the preview to the agent, allow edits, then submit the
 * final content to the send endpoint.
 */
public record ReplyPreviewRequest(

        @NotNull
        Long mailboxId,

        /**
         * Source ingress event this reply responds to.
         * When set, the backend derives the reply-to address from the event headers.
         * Preferred for normal customer replies.
         */
        Long sourceEventId,

        /**
         * Optional template id to use for rendering.
         * If null, falls back to templateCode, then to active CUSTOMER_REPLY.
         */
        Long templateId,

        /**
         * Optional template code to use for rendering.
         * Ignored when templateId is set.
         */
        String templateCode,

        /**
         * Optional subject override. When null, the template subject is used.
         * When provided, overrides the template's subject template.
         */
        String subjectOverride,

        /**
         * Optional plain-text body content to merge into the template's {replyBody} placeholder.
         * Used when the agent has pre-authored the reply body before requesting a preview.
         */
        String bodyText,

        /**
         * Optional HTML body content to merge into the template's {replyBody} placeholder.
         * When null and bodyText is present, bodyText will be HTML-escaped and used.
         */
        String bodyHtml
) {}
