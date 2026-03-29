package com.caseflow.email.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Input data carrier for {@link EmailIngressService#receiveEvent(IngressEmailData)}.
 *
 * <p>All threading headers (inReplyTo, references) are persisted in the event so that
 * Stage-2 processing can correctly resolve thread linkage and ticket attachment without
 * re-fetching the original message from the mail server.
 *
 * <p>The reply-to field is preserved to support correct outbound reply targeting:
 * replies go to replyTo if present, otherwise to rawFrom.
 */
public record IngressEmailData(

        /** RFC 2822 Message-ID header value. Required for idempotency. */
        String messageId,

        /** Raw From header (may include display name: "Jane Doe <jane@example.com>"). */
        String rawFrom,

        /** Raw To header (comma-separated if multiple recipients). */
        String rawTo,

        /** In-Reply-To header — messageId of the parent message this is a reply to. */
        String inReplyTo,

        /** References header — chain of ancestor messageIds (most recent last). */
        List<String> references,

        /** Reply-To header — preferred reply target address if different from From. */
        String replyTo,

        /** CC recipients (comma-separated). */
        String rawCc,

        /** Subject header. */
        String rawSubject,

        /** Plain-text body content. */
        String textBody,

        /** HTML body content. */
        String htmlBody,

        /** ID of the CaseFlow mailbox this message was received on. */
        Long mailboxId,

        /** When the message was received (from message Date header or server delivery time). */
        Instant receivedAt,

        /** Actual SMTP envelope recipient — may differ from the To: header. */
        String envelopeRecipient,

        /**
         * Attachments extracted from the message and already stored in object storage.
         * Null or empty for webhook-ingest events (webhook providers handle attachment storage separately).
         * Populated by {@code ImapMailboxPoller} for IMAP-polled messages.
         */
        List<IngressAttachmentData> attachments
) {

    /** Normalises a pipe-separated references string from rawReferences storage to a List. */
    public static String referencesToRaw(List<String> references) {
        if (references == null || references.isEmpty()) return null;
        return String.join("|", references);
    }

    /** Returns the attachments list, never null. */
    public List<IngressAttachmentData> safeAttachments() {
        return attachments != null ? attachments : Collections.emptyList();
    }
}
