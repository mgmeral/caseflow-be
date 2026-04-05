package com.caseflow.storage;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generates stable, structured object storage keys for ticket attachments.
 *
 * <h2>Two-stage key design</h2>
 * <pre>
 * Stage A — Staging (IMAP attachment ingested before routing):
 *   staging/mailboxes/{mailboxId}/precheck/{imapUid}/attachments/{uuid}/{safeFilename}
 *
 * Stage B — Final (after ticket routing and document creation):
 *   tickets/{ticketPublicId}/emails/{emailDocumentId}/attachments/{attachmentId}/{safeFilename}
 * </pre>
 *
 * <p>Stage A keys are written by {@link com.caseflow.email.service.ImapMailboxPoller}
 * during Stage-1 ingestion (before routing). Stage B keys are the long-term canonical form
 * using the stable database attachment ID — fully deterministic given the same inputs.
 * Promotion from Stage A → Stage B copies the object in storage and updates
 * {@code AttachmentMetadata.objectKey} and {@code storageStage} to {@code FINAL}.
 *
 * <h2>Direct-upload</h2>
 * <pre>
 *   tickets/{ticketPublicId}/attachments/{uuid}/{safeFilename}
 * </pre>
 * <p>Used by the manual upload endpoint when the file is not associated with any email.
 * A UUID provides uniqueness (no attachment DB ID is available at upload time).
 */
@Service
public class AttachmentKeyStrategy {

    /**
     * Generates a staging key used before the inbound email is routed to a ticket.
     *
     * <p>The {@code imapUid} acts as a precheck correlation ID — it is known before the
     * routing decision and ties all attachments for a given message together.
     *
     * @param mailboxId  mailbox that received the email
     * @param imapUid    IMAP UID of the message (stable within the folder)
     * @param fileName   original attachment file name
     * @return staging object key, e.g.
     *         {@code staging/mailboxes/1/precheck/12345/attachments/{uuid}/file.pdf}
     */
    public String stagingKey(Long mailboxId, long imapUid, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String safe = sanitize(fileName);
        return "staging/mailboxes/" + mailboxId + "/precheck/" + imapUid
                + "/attachments/" + uuid + "/" + safe;
    }

    /**
     * Generates the stable, deterministic final key once the ticket and email document are
     * known and the {@code AttachmentMetadata} record has been persisted (giving a DB ID).
     *
     * <p>The resulting key is fully deterministic given the same inputs — no random UUID.
     * The {@code attachmentId} is the primary key of the {@code attachment_metadata} row.
     *
     * @param ticketPublicId  ticket's stable public UUID
     * @param emailDocumentId MongoDB EmailDocument id
     * @param attachmentId    primary key of the {@code AttachmentMetadata} JPA entity
     * @param fileName        original attachment file name
     * @return final canonical object key, e.g.
     *         {@code tickets/{publicId}/emails/{docId}/attachments/42/file.pdf}
     */
    public String finalKey(UUID ticketPublicId, String emailDocumentId,
                           Long attachmentId, String fileName) {
        String safe = sanitize(fileName);
        return "tickets/" + ticketPublicId + "/emails/" + emailDocumentId
                + "/attachments/" + attachmentId + "/" + safe;
    }

    /**
     * Generates a stable key for a directly-uploaded attachment (no source email).
     *
     * <p>Used by the manual upload endpoint when the file is not associated with
     * any ingested email document.
     *
     * @param ticketPublicId ticket's stable public UUID
     * @param fileName       original attachment file name
     * @return direct-upload object key, e.g.
     *         {@code tickets/{publicId}/attachments/{uuid}/file.pdf}
     */
    public String directUploadKey(UUID ticketPublicId, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String safe = sanitize(fileName);
        return "tickets/" + ticketPublicId + "/attachments/" + uuid + "/" + safe;
    }

    private String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) return "unnamed";
        String safe = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }
}
