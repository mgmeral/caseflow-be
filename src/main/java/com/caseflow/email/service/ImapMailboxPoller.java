package com.caseflow.email.service;

import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.repository.EmailMailboxRepository;
import jakarta.mail.Address;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * IMAP polling MVP — polls configured mailboxes for new inbound messages.
 *
 * <p>Duplicate safety:
 * <ul>
 *   <li>UID-based: only fetches messages with UID &gt; {@code lastSeenUid}</li>
 *   <li>MessageId-based: {@code receiveEvent} is idempotent on messageId</li>
 * </ul>
 *
 * <p>Passwords are never logged.
 */
@Service
public class ImapMailboxPoller {

    private static final Logger log = LoggerFactory.getLogger(ImapMailboxPoller.class);

    private final EmailMailboxRepository mailboxRepository;
    private final EmailIngressService ingressService;

    public ImapMailboxPoller(EmailMailboxRepository mailboxRepository,
                             EmailIngressService ingressService) {
        this.mailboxRepository = mailboxRepository;
        this.ingressService = ingressService;
    }

    /**
     * Polls a single mailbox for new messages.
     * Updates {@code lastSeenUid}, {@code lastPollAt}, and {@code lastPollError} on the mailbox.
     * Safe to call concurrently — each mailbox is independently updated.
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

        try {
            store = openStore(mailbox);
            folder = openFolder(store, mailbox.getImapFolder());

            if (!(folder instanceof UIDFolder uidFolder)) {
                log.warn("Mailbox {} IMAP folder does not support UID operations — skipping", mailbox.getId());
                return;
            }

            long startUid = mailbox.getLastSeenUid() != null ? mailbox.getLastSeenUid() + 1 : 1L;
            Message[] messages = uidFolder.getMessagesByUID(startUid, UIDFolder.MAXUID);

            if (messages.length == 0) {
                log.debug("No new messages for mailbox {} since uid {}", mailbox.getId(), startUid);
                mailbox.setLastPollAt(Instant.now());
                mailbox.setLastPollError(null);
                mailboxRepository.save(mailbox);
                return;
            }

            log.info("Found {} new message(s) for mailbox {}", messages.length, mailbox.getId());

            long maxUidSeen = mailbox.getLastSeenUid() != null ? mailbox.getLastSeenUid() : 0L;

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

            mailbox.setLastSeenUid(maxUidSeen);
            mailbox.setLastPollAt(Instant.now());
            mailbox.setLastPollError(null);
            mailboxRepository.save(mailbox);

        } catch (Exception e) {
            log.error("IMAP poll failed for mailbox {} — {}", mailbox.getId(), e.getMessage(), e);
            mailbox.setLastPollAt(Instant.now());
            mailbox.setLastPollError(e.getMessage());
            mailboxRepository.save(mailbox);
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private void processMessage(EmailMailbox mailbox, Message message) throws MessagingException, IOException {
        String messageId = getHeader(message, "Message-ID");
        if (messageId == null || messageId.isBlank()) {
            // Generate a synthetic messageId to maintain idempotency
            messageId = "<synthetic-" + System.nanoTime() + "@" + mailbox.getAddress() + ">";
            log.warn("Message from mailbox {} has no Message-ID — using synthetic: {}", mailbox.getId(), messageId);
        }

        String inReplyTo   = getHeader(message, "In-Reply-To");
        String refsHeader  = getHeader(message, "References");
        String replyTo     = addressHeader(message.getReplyTo());
        String rawFrom     = addressHeader(message.getFrom());
        String rawTo       = recipientHeader(message, Message.RecipientType.TO);
        String rawCc       = recipientHeader(message, Message.RecipientType.CC);
        String subject     = message.getSubject();

        List<String> references = parseReferences(refsHeader);

        String[] textAndHtml = extractBodies(message);
        String textBody = textAndHtml[0];
        String htmlBody = textAndHtml[1];

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
                null  // envelopeRecipient — not available from IMAP headers directly
        );

        ingressService.receiveEvent(data);
        log.debug("Queued message for processing — messageId: '{}', from: '{}'", messageId, rawFrom);
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
        // References header contains space-separated message-ids
        for (String ref : refsHeader.trim().split("\\s+")) {
            if (!ref.isBlank()) result.add(ref.trim());
        }
        return result;
    }

    /** Extracts [textBody, htmlBody] from a message, handling multipart. */
    private String[] extractBodies(Part part) throws MessagingException, IOException {
        String[] result = {null, null};
        extractBodiesInto(part, result);
        return result;
    }

    private void extractBodiesInto(Part part, String[] result) throws MessagingException, IOException {
        String contentType = part.getContentType().toLowerCase();

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
}
