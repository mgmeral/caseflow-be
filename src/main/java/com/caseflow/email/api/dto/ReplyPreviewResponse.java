package com.caseflow.email.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response from POST /api/tickets/{ticketPublicId}/email/reply/preview.
 *
 * <p>Contains the fully rendered content the agent sees before sending.
 * The agent may edit the rendered content; the final request must carry
 * {@code contentWasEdited = true} so the audit trail is accurate.
 *
 * <h2>Placeholder diagnostics</h2>
 * {@code warnings} reports unresolved or unknown placeholders in the template.
 * An empty list means the template rendered cleanly.
 * A non-empty list does not block sending — the agent should review.
 */
public record ReplyPreviewResponse(

        UUID ticketPublicId,

        /** EMAIL_DOCUMENT or OUTBOUND_DISPATCH (type of sourceDetailId). */
        String sourceDetailType,

        /** Detail id of the source email being replied to (may be null for proactive). */
        String sourceDetailId,

        Long mailboxId,
        String mailboxName,
        String mailboxAddress,

        /** Backend-derived recipient address. FE must NOT override this for normal reply. */
        String derivedToAddress,

        /** Sending address (from the mailbox). */
        String derivedFromAddress,

        /** Fully rendered subject. */
        String subject,

        /** Fully rendered plain-text body. */
        String bodyText,

        /** Fully rendered HTML body. */
        String bodyHtml,

        /** Template that was applied to produce this preview. */
        TemplateInfo templateInfo,

        /** Placeholder/template warnings — empty when all resolved cleanly. */
        List<String> warnings,

        /** Placeholder diagnostic entries — one per unresolved placeholder. */
        List<PlaceholderDiagnostic> placeholderDiagnostics,

        /** True when the agent can edit the rendered subject/body before sending. */
        boolean isEditable,

        Instant previewGeneratedAt

) {
    /** Template applied for this preview. */
    public record TemplateInfo(
            Long templateId,
            String templateCode,
            String templateName
    ) {}

    /** Describes a single placeholder that did not resolve cleanly. */
    public record PlaceholderDiagnostic(
            String placeholder,
            /** UNRESOLVED, EMPTY, UNKNOWN */
            String severity,
            String message
    ) {}
}
