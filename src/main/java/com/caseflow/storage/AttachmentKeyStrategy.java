package com.caseflow.storage;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Generates stable, structured object storage keys for ticket attachments.
 *
 * <h2>Two-stage key design</h2>
 * <pre>
 * Stage A — Staging (IMAP attachment ingested before routing):
 *   staging/mailboxes/{mailboxId}/uid-{imapUid}/attachments/{uuid}_{safeName}
 *
 * Stage B — Final (after ticket routing and document creation):
 *   tickets/{ticketPublicId}/emails/{emailDocumentId}/attachments/{uuid}_{safeName}
 * </pre>
 *
 * <p>Stage A keys are written by {@link com.caseflow.email.service.ImapMailboxPoller}
 * during Stage-1 ingestion. Stage B keys are the long-term canonical form.
 * Migration from Stage A → Stage B is done by copying the object in storage
 * and updating {@code AttachmentMetadata.objectKey}.
 */
@Service
public class AttachmentKeyStrategy {

    /**
     * Generates a staging key used before the inbound email is routed to a ticket.
     *
     * @param mailboxId  mailbox that received the email
     * @param imapUid    IMAP UID of the message (available from UIDFolder at poll time)
     * @param fileName   original attachment file name
     * @return staging object key, e.g. {@code staging/mailboxes/1/uid-12345/attachments/...}
     */
    public String stagingKey(Long mailboxId, long imapUid, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String safe = sanitize(fileName);
        return "staging/mailboxes/" + mailboxId + "/uid-" + imapUid
                + "/attachments/" + uuid + "_" + safe;
    }

    /**
     * Generates the stable final key once the ticket and email document are known.
     *
     * @param ticketPublicId  ticket's stable public UUID
     * @param emailDocumentId MongoDB EmailDocument id
     * @param fileName        original attachment file name
     * @return final canonical object key
     */
    public String finalKey(UUID ticketPublicId, String emailDocumentId, String fileName) {
        String uuid = UUID.randomUUID().toString();
        String safe = sanitize(fileName);
        return "tickets/" + ticketPublicId + "/emails/" + emailDocumentId
                + "/attachments/" + uuid + "_" + safe;
    }

    private String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) return "unnamed";
        String safe = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }
}
