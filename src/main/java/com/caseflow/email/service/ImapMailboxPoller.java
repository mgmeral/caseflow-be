package com.caseflow.email.service;

import com.caseflow.email.api.dto.MailboxConnectionTestResponse;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.storage.ObjectStorageService;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IMAP mailbox poller — fetches new inbound messages and routes them through the ingress pipeline.
 *
 * <h2>Duplicate safety</h2>
 * <ul>
 *   <li>UID-based: only fetches messages with UID &gt; {@code lastSeenUid}
 *   <li>MessageId-based: {@code receiveEvent} is idempotent on messageId
 * </ul>
 *
 * <h2>Multi-instance safety</h2>
 * The caller (ImapPollingScheduler) claims a DB-level lease before invoking {@code pollMailbox}.
 * This method releases the lease in its {@code finally} block, ensuring the lock is always cleared
 * even if the JVM crashes mid-poll (the lease expires automatically after TTL).
 *
 * <h2>First-time onboarding</h2>
 * When {@code lastSeenUid} is null:
 * <ul>
 *   <li>{@code START_FROM_LATEST}: advances cursor to the current highest UID without ingesting history.
 *   <li>{@code BACKFILL_ALL}: starts from UID 1 and ingests the entire inbox.
 * </ul>
 *
 * <p>Passwords are never logged.
 */
@Service
public class ImapMailboxPoller {

    private static final Logger log = LoggerFactory.getLogger(ImapMailboxPoller.class);

    /** Attachments larger than this limit are skipped to avoid OOM on a single large file. */
    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024; // 25 MB

    private final EmailMailboxRepository mailboxRepository;
    private final EmailIngressService ingressService;
    private final ObjectStorageService objectStorageService;

    public ImapMailboxPoller(EmailMailboxRepository mailboxRepository,
                             EmailIngressService ingressService,
                             ObjectStorageService objectStorageService) {
        this.mailboxRepository = mailboxRepository;
        this.ingressService = ingressService;
        this.objectStorageService = objectStorageService;
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    /**
     * Polls a single mailbox for new messages.
     * Always updates {@code lastPollAt} and clears the poll lease in the {@code finally} block.
     */
    @Transactional
    public void pollMailbox(EmailMailbox mailbox) {
        if (!Boolean.TRUE.equals(mailbox.getPollingEnabled())) return;
        if (mailbox.getImapHost() == null || mailbox.getImapUsername() == null) {
            log.warn("Mailbox {} ({}) has polling enabled but is missing IMAP credentials — skipping",
                    mailbox.getId(), mailbox.getAddress());
            return;
        }

        log.info("Polling IMAP mailbox {} ({}) — folder: {}", mailbox.getId(), mailbox.getAddress(),
                mailbox.getImapFolder());

        Store store = null;
        Folder folder = null;
        long maxUidSeen = mailbox.getLastSeenUid() != null ? mailbox.getLastSeenUid() : 0L;
        String pollError = null;

        try {
            store = openStore(mailbox);
            folder = openFolder(store, mailbox.getImapFolder());

            if (!(folder instanceof UIDFolder uidFolder)) {
                log.warn("Mailbox {} IMAP folder does not support UID operations — skipping", mailbox.getId());
                return;
            }

            // ── First-time onboarding ────────────────────────────────────────
            if (mailbox.getLastSeenUid() == null) {
                InitialSyncStrategy strategy = mailbox.getInitialSyncStrategy() != null
                        ? mailbox.getInitialSyncStrategy()
                        : InitialSyncStrategy.START_FROM_LATEST;

                if (strategy == InitialSyncStrategy.START_FROM_LATEST) {
                    maxUidSeen = resolveLatestUid(folder, uidFolder);
                    log.info("Mailbox {} first poll — START_FROM_LATEST strategy; advancing cursor to UID {}; "
                            + "no historical messages will be ingested", mailbox.getId(), maxUidSeen);
                    return; // no ingestion; cursor updated in finally
                }
                // BACKFILL_ALL: proceed with startUid = 1 (maxUidSeen stays 0)
                log.info("Mailbox {} first poll — BACKFILL_ALL strategy; ingesting from UID 1",
                        mailbox.getId());
            }

            // ── Fetch new messages ───────────────────────────────────────────
            long startUid = maxUidSeen + 1;
            Message[] messages = uidFolder.getMessagesByUID(startUid, UIDFolder.MAXUID);

            if (messages.length == 0) {
                log.debug("No new messages for mailbox {} since uid {}", mailbox.getId(), maxUidSeen);
                return;
            }

            log.info("Found {} new message(s) for mailbox {}", messages.length, mailbox.getId());

            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                try {
                    processMessage(mailbox, message);
                    if (uid > maxUidSeen) maxUidSeen = uid;
                } catch (Exception e) {
                    log.error("Failed to ingest message uid={} from mailbox {} — {}",
                            uid, mailbox.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("IMAP poll failed for mailbox {} — {}", mailbox.getId(), e.getMessage(), e);
            pollError = e.getMessage();
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
            // Always persist cursor advance, clear lease, and update poll timestamps
            if (maxUidSeen > 0 || mailbox.getLastSeenUid() != null) {
                mailbox.setLastSeenUid(maxUidSeen > 0 ? maxUidSeen : mailbox.getLastSeenUid());
            }
            mailbox.setLastPollAt(Instant.now());
            mailbox.setLastPollError(pollError);
            mailbox.setPollLockedBy(null);
            mailbox.setPollLeasedUntil(null);
            mailboxRepository.save(mailbox);
        }
    }

    // ── Connection test ───────────────────────────────────────────────────────

    /**
     * Tests IMAP connectivity and folder access for the given mailbox.
     * Returns a structured result without throwing — suitable for operator-facing health checks.
     */
    public MailboxConnectionTestResponse testImapConnection(EmailMailbox mailbox) {
        Store store = null;
        Folder folder = null;
        try {
            store = openStore(mailbox);
            folder = openFolder(store, mailbox.getImapFolder());
            int messageCount = folder.getMessageCount();
            String folderName = mailbox.getImapFolder() != null ? mailbox.getImapFolder() : "INBOX";
            return new MailboxConnectionTestResponse(true,
                    "Connection successful. Folder '" + folderName + "' contains " + messageCount + " message(s).",
                    Instant.now());
        } catch (Exception e) {
            log.warn("IMAP connection test failed for mailbox {} — {}", mailbox.getId(), e.getMessage());
            return new MailboxConnectionTestResponse(false, sanitizeErrorMessage(e.getMessage()), Instant.now());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    // ── Message processing ────────────────────────────────────────────────────

    private void processMessage(EmailMailbox mailbox, Message message) throws MessagingException, IOException {
        String messageId = getHeader(message, "Message-ID");
        if (messageId == null || messageId.isBlank()) {
            messageId = "<synthetic-" + System.nanoTime() + "@" + mailbox.getAddress() + ">";
            log.warn("Message from mailbox {} has no Message-ID — using synthetic: {}", mailbox.getId(), messageId);
        }

        String inReplyTo  = getHeader(message, "In-Reply-To");
        String refsHeader = getHeader(message, "References");
        String replyTo    = addressHeader(message.getReplyTo());
        String rawFrom    = addressHeader(message.getFrom());
        String rawTo      = recipientHeader(message, Message.RecipientType.TO);
        String rawCc      = recipientHeader(message, Message.RecipientType.CC);
        String subject    = message.getSubject();

        List<String> references = parseReferences(refsHeader);

        String[] textAndHtml = extractBodies(message);
        String textBody = textAndHtml[0];
        String htmlBody = textAndHtml[1];

        List<IngressAttachmentData> attachments = extractAndStoreAttachments(message, mailbox.getId());

        Instant receivedAt = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant()
                : Instant.now();

        IngressEmailData data = new IngressEmailData(
                messageId.trim(),
                rawFrom,
                rawTo,
                inReplyTo,
                references,
                replyTo,
                rawCc,
                subject,
                textBody,
                htmlBody,
                mailbox.getId(),
                receivedAt,
                null,        // envelopeRecipient — not available from IMAP headers directly
                attachments.isEmpty() ? null : attachments
        );

        ingressService.receiveEvent(data);
        log.debug("Queued message for processing — messageId: '{}', from: '{}', attachments: {}",
                messageId, rawFrom, attachments.size());
    }

    // ── Attachment extraction ─────────────────────────────────────────────────

    /**
     * Extracts attachment parts from the message, stores their binary content in object storage,
     * and returns the metadata list.  Body parts (text/plain, text/html) are skipped here —
     * they are extracted separately by {@link #extractBodies(Part)}.
     */
    private List<IngressAttachmentData> extractAndStoreAttachments(Part message, Long mailboxId)
            throws MessagingException, IOException {
        List<IngressAttachmentData> result = new ArrayList<>();
        collectAttachments(message, mailboxId, result);
        return result;
    }

    private void collectAttachments(Part part, Long mailboxId, List<IngressAttachmentData> result)
            throws MessagingException, IOException {
        String disposition = part.getDisposition();
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (Part.INLINE.equalsIgnoreCase(disposition) && part.getFileName() != null);

        if (isAttachment && part.getFileName() != null) {
            storeAttachment(part, mailboxId, result);
            return;
        }

        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collectAttachments(mp.getBodyPart(i), mailboxId, result);
            }
        }
        // text/plain, text/html, and other non-attachment inline parts are skipped here
    }

    private void storeAttachment(Part part, Long mailboxId, List<IngressAttachmentData> result) {
        try {
            String rawFileName = part.getFileName();
            String fileName = MimeUtility.decodeText(rawFileName);
            String contentType = part.getContentType() != null
                    ? part.getContentType().split(";")[0].trim()
                    : "application/octet-stream";

            byte[] data;
            try (InputStream is = part.getInputStream()) {
                data = is.readAllBytes();
            }

            if (data.length > MAX_ATTACHMENT_BYTES) {
                log.warn("Attachment '{}' from mailbox {} exceeds {}MB — skipping",
                        fileName, mailboxId, MAX_ATTACHMENT_BYTES / (1024 * 1024));
                return;
            }

            String objectKey = "email-inbound/" + mailboxId + "/" + UUID.randomUUID()
                    + "_" + sanitizeFileName(fileName);
            objectStorageService.store(objectKey, data, contentType);

            result.add(new IngressAttachmentData(fileName, objectKey, contentType, (long) data.length));
            log.debug("Stored attachment '{}' — objectKey: '{}', size: {} bytes", fileName, objectKey, data.length);

        } catch (Exception e) {
            log.warn("Failed to extract/store attachment from mailbox {} — {}", mailboxId, e.getMessage(), e);
        }
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "unnamed";
        String safe = fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    // ── IMAP connection helpers ───────────────────────────────────────────────

    private Store openStore(EmailMailbox mailbox) throws MessagingException {
        String protocol = Boolean.TRUE.equals(mailbox.getImapUseSsl()) ? "imaps" : "imap";
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", protocol);
        props.setProperty("mail." + protocol + ".host", mailbox.getImapHost());
        if (mailbox.getImapPort() != null) {
            props.setProperty("mail." + protocol + ".port", String.valueOf(mailbox.getImapPort()));
        }
        props.setProperty("mail." + protocol + ".timeout", "15000");
        props.setProperty("mail." + protocol + ".connectiontimeout", "15000");

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        store.connect(mailbox.getImapHost(), mailbox.getImapUsername(), mailbox.getImapPassword());
        return store;
    }

    private Folder openFolder(Store store, String folderName) throws MessagingException {
        String name = (folderName != null && !folderName.isBlank()) ? folderName : "INBOX";
        Folder folder = store.getFolder(name);
        folder.open(Folder.READ_ONLY);
        return folder;
    }

    /**
     * Returns the highest UID currently in the folder, or 0 if the folder is empty.
     * Used to advance the cursor without ingesting historical messages.
     */
    private long resolveLatestUid(Folder folder, UIDFolder uidFolder) throws MessagingException {
        int count = folder.getMessageCount();
        if (count <= 0) return 0L;
        Message lastMessage = folder.getMessage(count);
        return uidFolder.getUID(lastMessage);
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try { folder.close(false); } catch (Exception ignored) {}
        }
    }

    private void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try { store.close(); } catch (Exception ignored) {}
        }
    }

    // ── Message parsing helpers ───────────────────────────────────────────────

    private String getHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        if (values == null || values.length == 0) return null;
        return values[0].trim();
    }

    private String addressHeader(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return null;
        return Arrays.stream(addresses)
                .map(Address::toString)
                .collect(Collectors.joining(", "));
    }

    private String recipientHeader(Message message, Message.RecipientType type) throws MessagingException {
        Address[] addresses = message.getRecipients(type);
        return addressHeader(addresses);
    }

    private List<String> parseReferences(String refsHeader) {
        if (refsHeader == null || refsHeader.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String ref : refsHeader.trim().split("\\s+")) {
            if (!ref.isBlank()) result.add(ref.trim());
        }
        return result;
    }

    /** Extracts [textBody, htmlBody] from a message, handling multipart recursively. */
    private String[] extractBodies(Part part) throws MessagingException, IOException {
        String[] result = {null, null};
        extractBodiesInto(part, result);
        return result;
    }

    private void extractBodiesInto(Part part, String[] result) throws MessagingException, IOException {
        // Skip attachment parts — those are handled by extractAndStoreAttachments
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(disposition)) return;

        if (part.isMimeType("text/plain") && result[0] == null) {
            result[0] = (String) part.getContent();
        } else if (part.isMimeType("text/html") && result[1] == null) {
            result[1] = (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                extractBodiesInto(multipart.getBodyPart(i), result);
            }
        }
    }

    /** Removes potential credential leakage from IMAP error messages before returning to callers. */
    private String sanitizeErrorMessage(String message) {
        if (message == null) return "Connection failed";
        return message
                .replaceAll("(?i)password=\\S+", "password=[REDACTED]")
                .replaceAll("(?i)pwd=\\S+", "pwd=[REDACTED]");
    }
}
