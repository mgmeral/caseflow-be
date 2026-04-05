package com.caseflow.email.service;

import com.caseflow.email.api.dto.MailboxConnectionTestResponse;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.InitialSyncStrategy;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.storage.AttachmentKeyStrategy;
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
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * IMAP mailbox poller — fetches new inbound messages and routes them through the ingress pipeline.
 *
 * <h2>Pre-routing guard (P0 fix)</h2>
 * Before parsing message bodies or uploading attachments, this poller runs a lightweight
 * routing precheck using only the message headers (From, In-Reply-To, References).
 * <ul>
 *   <li>ACCEPT (CREATE_TICKET / LINK_TO_TICKET): full parse + attachment upload + ingest.</li>
 *   <li>QUARANTINE: parse bodies but skip attachment upload; persist minimal ingress event for
 *       operator review.</li>
 *   <li>IGNORE / REJECT: skip entirely — no storage write, no DB persistence.</li>
 * </ul>
 *
 * <h2>Attachment gating</h2>
 * Attachments are NEVER uploaded to object storage until the routing precheck returns ACCEPT.
 *
 * <h2>Duplicate safety</h2>
 * UID-based cursor prevents reprocessing. {@code receiveEvent} provides a secondary messageId
 * idempotency guard.
 *
 * <h2>Multi-instance safety</h2>
 * The caller (ImapPollingScheduler) claims a DB-level lease before invoking {@code pollMailbox}.
 * The lease is released in the {@code finally} block.
 *
 * <h2>Initial sync strategies</h2>
 * <ul>
 *   <li>{@code NEW_MESSAGES_ONLY}: advance cursor to current max UID, ingest nothing.</li>
 *   <li>{@code SCAN_FROM_START}: start from UID 1 (full history).</li>
 *   <li>{@code SCAN_LAST_1/3/7_DAY}: use IMAP SEARCH to find recent messages by date.</li>
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
    private final AttachmentKeyStrategy keyStrategy;
    private final EmailRoutingService routingService;

    public ImapMailboxPoller(EmailMailboxRepository mailboxRepository,
                             EmailIngressService ingressService,
                             ObjectStorageService objectStorageService,
                             AttachmentKeyStrategy keyStrategy,
                             EmailRoutingService routingService) {
        this.mailboxRepository = mailboxRepository;
        this.ingressService = ingressService;
        this.objectStorageService = objectStorageService;
        this.keyStrategy = keyStrategy;
        this.routingService = routingService;
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
            log.warn("IMAP_POLL mailbox {} ({}) has polling enabled but missing IMAP credentials — skipping",
                    mailbox.getId(), mailbox.getAddress());
            return;
        }

        log.info("IMAP_POLL start — mailbox: {} ({}), folder: {}",
                mailbox.getId(), mailbox.getAddress(), mailbox.getImapFolder());

        Store store = null;
        Folder folder = null;
        long maxUidSeen = mailbox.getLastSeenUid() != null ? mailbox.getLastSeenUid() : 0L;
        String pollError = null;

        try {
            store = openStore(mailbox);
            folder = openFolder(store, mailbox.getImapFolder());

            if (!(folder instanceof UIDFolder uidFolder)) {
                log.warn("IMAP_POLL mailbox {} — folder does not support UID operations — skipping",
                        mailbox.getId());
                return;
            }

            // ── First-time onboarding (null cursor) ──────────────────────────
            if (mailbox.getLastSeenUid() == null) {
                InitialSyncStrategy strategy = mailbox.getInitialSyncStrategy() != null
                        ? mailbox.getInitialSyncStrategy()
                        : InitialSyncStrategy.NEW_MESSAGES_ONLY;

                if (strategy == InitialSyncStrategy.NEW_MESSAGES_ONLY) {
                    maxUidSeen = resolveLatestUid(folder, uidFolder);
                    log.info("IMAP_CURSOR_INIT mailbox {} — NEW_MESSAGES_ONLY: cursor advanced to UID {}; "
                            + "no historical messages will be ingested", mailbox.getId(), maxUidSeen);
                    return; // cursor updated in finally
                }

                if (strategy == InitialSyncStrategy.SCAN_LAST_1_DAY
                        || strategy == InitialSyncStrategy.SCAN_LAST_3_DAYS
                        || strategy == InitialSyncStrategy.SCAN_LAST_7_DAYS) {
                    int days = strategyToDays(strategy);
                    maxUidSeen = resolveLatestUid(folder, uidFolder); // advance cursor after scan
                    Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
                    log.info("IMAP_CURSOR_INIT mailbox {} — {}: scanning last {} day(s) since {}; cursor: {}",
                            mailbox.getId(), strategy, days, cutoff, maxUidSeen);
                    Message[] recentMessages = searchSince(folder, cutoff);
                    log.info("IMAP_POLL mailbox {} — {} scan found {} candidate message(s)",
                            mailbox.getId(), strategy, recentMessages.length);
                    for (Message message : recentMessages) {
                        long uid = uidFolder.getUID(message);
                        try {
                            processMessage(mailbox, message, uid);
                        } catch (Exception e) {
                            log.error("IMAP_POLL failed to ingest message uid={} mailbox={} — {}",
                                    uid, mailbox.getId(), e.getMessage(), e);
                        }
                    }
                    return; // cursor updated in finally
                }

                // SCAN_FROM_START: proceed with startUid = 1 (maxUidSeen stays 0)
                log.info("IMAP_CURSOR_INIT mailbox {} — SCAN_FROM_START: ingesting from UID 1",
                        mailbox.getId());
            }

            // ── Fetch new messages via UID cursor ────────────────────────────
            long startUid = maxUidSeen + 1;
            Message[] messages = uidFolder.getMessagesByUID(startUid, UIDFolder.MAXUID);

            if (messages.length == 0) {
                log.debug("IMAP_POLL mailbox {} — no new messages since UID {}", mailbox.getId(), maxUidSeen);
                return;
            }

            log.info("IMAP_POLL mailbox {} — {} new message(s) since UID {}",
                    messages.length, mailbox.getId(), maxUidSeen);

            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                try {
                    processMessage(mailbox, message, uid);
                    if (uid > maxUidSeen) maxUidSeen = uid;
                } catch (Exception e) {
                    log.error("IMAP_POLL failed to ingest message uid={} mailbox={} — {}",
                            uid, mailbox.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("IMAP_POLL failed for mailbox {} — {}", mailbox.getId(), e.getMessage(), e);
            pollError = e.getMessage();
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
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
     * Tests IMAP connectivity and folder access.
     * Returns a structured result without throwing — suitable for operator health checks.
     */
    public MailboxConnectionTestResponse testImapConnection(EmailMailbox mailbox) {
        Store store = null;
        Folder folder = null;
        try {
            store = openStore(mailbox);
            folder = openFolder(store, mailbox.getImapFolder());
            int messageCount = folder.getMessageCount();
            String folderName = mailbox.getImapFolder() != null ? mailbox.getImapFolder() : "INBOX";
            log.info("IMAP_TEST_CONNECTION success — mailbox: {}, folder: '{}', messages: {}",
                    mailbox.getId(), folderName, messageCount);
            return new MailboxConnectionTestResponse(true,
                    "IMAP connection successful. Folder '" + folderName + "' contains " + messageCount + " message(s).",
                    Instant.now());
        } catch (Exception e) {
            log.warn("IMAP_TEST_CONNECTION failed — mailbox: {} — {}", mailbox.getId(), e.getMessage());
            return new MailboxConnectionTestResponse(false, sanitizeErrorMessage(e.getMessage()), Instant.now());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    // ── Message processing ────────────────────────────────────────────────────

    /**
     * Pre-routing guard: parse headers, run routing precheck, then decide whether to do
     * full ingest (bodies + attachments), quarantine-ingest (bodies only), or skip entirely.
     *
     * <p>Attachments are NEVER uploaded before the routing precheck returns ACCEPT.
     */
    private void processMessage(EmailMailbox mailbox, Message message, long imapUid) throws MessagingException, IOException {
        String messageId = getHeader(message, "Message-ID");
        if (messageId == null || messageId.isBlank()) {
            messageId = "<synthetic-" + System.nanoTime() + "@" + mailbox.getAddress() + ">";
            log.warn("IMAP_POLL message has no Message-ID in mailbox {} — using synthetic: {}",
                    mailbox.getId(), messageId);
        }
        messageId = messageId.trim();

        // ── Parse minimal headers for routing precheck ───────────────────────
        String inReplyTo  = getHeader(message, "In-Reply-To");
        String refsHeader = getHeader(message, "References");
        String replyTo    = addressHeader(message.getReplyTo());
        String rawFrom    = addressHeader(message.getFrom());
        String rawTo      = recipientHeader(message, Message.RecipientType.TO);
        String rawCc      = recipientHeader(message, Message.RecipientType.CC);
        String subject    = message.getSubject();
        List<String> references = parseReferences(refsHeader);
        Instant receivedAt = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant()
                : Instant.now();

        // ── Pre-routing decision (headers only — before any body or attachment work) ──
        RoutingResult precheck = routingService.routeHeaders(rawFrom, replyTo, inReplyTo, references, mailbox.getId());

        switch (precheck.action()) {
            case IGNORE, REJECT -> {
                log.info("INGRESS_PRECHECK_REJECT mailbox {} — messageId: '{}', from: '{}', action: {} — skipped",
                        mailbox.getId(), messageId, rawFrom, precheck.action());
                log.debug("ATTACHMENT_UPLOAD_SKIPPED mailbox {} — messageId: '{}' ({})",
                        mailbox.getId(), messageId, precheck.action());
                return; // no body parse, no upload, no DB write
            }
            case QUARANTINE -> {
                log.info("INGRESS_PRECHECK_REJECT mailbox {} — messageId: '{}', from: '{}', action: QUARANTINE — {}",
                        mailbox.getId(), messageId, rawFrom, precheck.quarantineReason());
                log.debug("ATTACHMENT_UPLOAD_SKIPPED mailbox {} — messageId: '{}' (QUARANTINE)",
                        mailbox.getId(), messageId);
                // Persist with body for operator review — no attachment upload
                String[] textAndHtml = extractBodies(message);
                IngressEmailData data = new IngressEmailData(
                        messageId, rawFrom, rawTo, inReplyTo, references, replyTo, rawCc,
                        subject, textAndHtml[0], textAndHtml[1],
                        mailbox.getId(), receivedAt, null, null);
                ingressService.receiveEvent(data);
                return;
            }
            case CREATE_TICKET, LINK_TO_TICKET -> {
                log.info("INGRESS_PRECHECK_ACCEPT mailbox {} — messageId: '{}', from: '{}', action: {}",
                        mailbox.getId(), messageId, rawFrom, precheck.action());
                // Full ingest: parse bodies AND upload attachments
                String[] textAndHtml = extractBodies(message);
                List<IngressAttachmentData> attachments = extractAndStoreAttachments(message, mailbox.getId(), imapUid);
                if (!attachments.isEmpty()) {
                    log.info("ATTACHMENT_UPLOAD_ALLOWED mailbox {} — messageId: '{}', count: {}",
                            mailbox.getId(), messageId, attachments.size());
                } else {
                    log.debug("ATTACHMENT_UPLOAD_SKIPPED mailbox {} — messageId: '{}' (no attachments)",
                            mailbox.getId(), messageId);
                }
                IngressEmailData data = new IngressEmailData(
                        messageId, rawFrom, rawTo, inReplyTo, references, replyTo, rawCc,
                        subject, textAndHtml[0], textAndHtml[1],
                        mailbox.getId(), receivedAt, null,
                        attachments.isEmpty() ? null : attachments);
                ingressService.receiveEvent(data);
                log.debug("IMAP_POLL queued message — messageId: '{}', from: '{}', attachments: {}",
                        messageId, rawFrom, attachments.size());
            }
        }
    }

    // ── Attachment extraction ─────────────────────────────────────────────────

    /**
     * Extracts attachment parts from the message, stores their binary content in object storage,
     * and returns the metadata list. Only called after routing precheck returns ACCEPT.
     */
    private List<IngressAttachmentData> extractAndStoreAttachments(Part message, Long mailboxId, long imapUid)
            throws MessagingException, IOException {
        List<IngressAttachmentData> result = new ArrayList<>();
        collectAttachments(message, mailboxId, imapUid, result);
        return result;
    }

    private void collectAttachments(Part part, Long mailboxId, long imapUid, List<IngressAttachmentData> result)
            throws MessagingException, IOException {
        String disposition = part.getDisposition();
        boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (Part.INLINE.equalsIgnoreCase(disposition) && part.getFileName() != null);

        if (isAttachment && part.getFileName() != null) {
            storeAttachment(part, mailboxId, imapUid, result);
            return;
        }

        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collectAttachments(mp.getBodyPart(i), mailboxId, imapUid, result);
            }
        }
    }

    private void storeAttachment(Part part, Long mailboxId, long imapUid, List<IngressAttachmentData> result) {
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
                log.warn("ATTACHMENT_UPLOAD_SKIPPED attachment '{}' from mailbox {} exceeds {}MB — skipping",
                        fileName, mailboxId, MAX_ATTACHMENT_BYTES / (1024 * 1024));
                return;
            }

            String objectKey = keyStrategy.stagingKey(mailboxId, imapUid, fileName);
            objectStorageService.store(objectKey, data, contentType);

            result.add(new IngressAttachmentData(fileName, objectKey, contentType, (long) data.length));
            log.debug("ATTACHMENT_UPLOAD_ALLOWED stored '{}' — key: '{}', size: {} bytes",
                    fileName, objectKey, data.length);

        } catch (Exception e) {
            log.warn("IMAP_POLL failed to extract/store attachment from mailbox {} — {}",
                    mailboxId, e.getMessage(), e);
        }
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

    private long resolveLatestUid(Folder folder, UIDFolder uidFolder) throws MessagingException {
        int count = folder.getMessageCount();
        if (count <= 0) return 0L;
        Message lastMessage = folder.getMessage(count);
        return uidFolder.getUID(lastMessage);
    }

    /**
     * Uses IMAP SEARCH (SINCE) to find messages received on or after the cutoff.
     * Falls back to an empty array if the server does not support SEARCH.
     */
    private Message[] searchSince(Folder folder, Instant cutoff) {
        try {
            ReceivedDateTerm since = new ReceivedDateTerm(ComparisonTerm.GE, Date.from(cutoff));
            return folder.search(since);
        } catch (Exception e) {
            log.warn("IMAP_POLL SEARCH SINCE not supported by server — falling back to empty scan: {}",
                    e.getMessage());
            return new Message[0];
        }
    }

    private static int strategyToDays(InitialSyncStrategy strategy) {
        return switch (strategy) {
            case SCAN_LAST_1_DAY -> 1;
            case SCAN_LAST_3_DAYS -> 3;
            case SCAN_LAST_7_DAYS -> 7;
            default -> throw new IllegalArgumentException("Not a SCAN_LAST strategy: " + strategy);
        };
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

    private String sanitizeErrorMessage(String message) {
        if (message == null) return "Connection failed";
        return message
                .replaceAll("(?i)password=\\S+", "password=[REDACTED]")
                .replaceAll("(?i)pwd=\\S+", "pwd=[REDACTED]");
    }
}
