package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.domain.EmailDirection;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.EmailMailbox;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.email.repository.EmailMailboxRepository;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import com.caseflow.workflow.state.TicketSystemTransitionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class EmailIngressServiceImpl implements EmailIngressService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngressServiceImpl.class);

    private static final TypeReference<List<IngressAttachmentData>> ATTACHMENT_LIST_TYPE =
            new TypeReference<>() {};

    private final EmailIngressEventRepository eventRepository;
    private final EmailDocumentRepository documentRepository;
    private final TicketRepository ticketRepository;
    private final EmailMailboxRepository mailboxRepository;
    private final CustomerEmailSettingsRepository settingsRepository;
    private final EmailRoutingService routingService;
    private final EmailThreadingService threadingService;
    private final LoopDetectionService loopDetectionService;
    private final TicketHistoryService historyService;
    private final TicketSystemTransitionService systemTransitionService;
    private final AttachmentService attachmentService;
    private final NotificationService notificationService;
    private final EmailMetrics metrics;
    private final ObjectMapper objectMapper;

    public EmailIngressServiceImpl(EmailIngressEventRepository eventRepository,
                                   EmailDocumentRepository documentRepository,
                                   TicketRepository ticketRepository,
                                   EmailMailboxRepository mailboxRepository,
                                   CustomerEmailSettingsRepository settingsRepository,
                                   EmailRoutingService routingService,
                                   EmailThreadingService threadingService,
                                   LoopDetectionService loopDetectionService,
                                   TicketHistoryService historyService,
                                   TicketSystemTransitionService systemTransitionService,
                                   AttachmentService attachmentService,
                                   NotificationService notificationService,
                                   EmailMetrics metrics,
                                   ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.documentRepository = documentRepository;
        this.ticketRepository = ticketRepository;
        this.mailboxRepository = mailboxRepository;
        this.settingsRepository = settingsRepository;
        this.routingService = routingService;
        this.threadingService = threadingService;
        this.loopDetectionService = loopDetectionService;
        this.historyService = historyService;
        this.systemTransitionService = systemTransitionService;
        this.attachmentService = attachmentService;
        this.notificationService = notificationService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    // ── Stage 1 ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EmailIngressEvent receiveEvent(IngressEmailData data) {
        return eventRepository.findByMessageId(data.messageId())
                .orElseGet(() -> {
                    EmailIngressEvent event = new EmailIngressEvent();
                    event.setMessageId(data.messageId());
                    event.setRawFrom(data.rawFrom());
                    event.setRawTo(data.rawTo());
                    event.setInReplyTo(data.inReplyTo());
                    event.setRawReferences(IngressEmailData.referencesToRaw(data.references()));
                    event.setRawReplyTo(data.replyTo());
                    event.setRawCc(data.rawCc());
                    event.setRawSubject(data.rawSubject());
                    event.setTextBody(data.textBody());
                    event.setHtmlBody(data.htmlBody());
                    event.setMailboxId(data.mailboxId());
                    event.setEnvelopeRecipient(data.envelopeRecipient());
                    event.setReceivedAt(data.receivedAt() != null ? data.receivedAt() : Instant.now());
                    event.setStatus(IngressEventStatus.RECEIVED);

                    // Serialize attachment metadata for Stage 2 processing
                    List<IngressAttachmentData> attachments = data.safeAttachments();
                    if (!attachments.isEmpty()) {
                        event.setAttachmentsJson(serializeAttachments(attachments));
                    }

                    EmailIngressEvent saved = eventRepository.save(event);
                    log.info("Ingress event stored — eventId: {}, messageId: '{}', attachments: {}",
                            saved.getId(), data.messageId(), attachments.size());
                    metrics.inboundReceived();
                    return saved;
                });
    }

    // ── Stage 2 ───────────────────────────────────────────────────────────────

    /**
     * Processes a single ingress event in its own independent transaction ({@code REQUIRES_NEW}).
     *
     * <p>Using {@code REQUIRES_NEW} is critical: callers such as
     * {@link com.caseflow.email.scheduler.EmailIngressRetryScheduler} are themselves
     * {@code @Transactional}. If this method joined that outer transaction, a constraint violation
     * on <em>any</em> event in the batch would mark the entire shared transaction as rollback-only,
     * causing every event's work (including committed tickets) to be rolled back on commit and the
     * duplicate-ticket creation cycle to restart.
     *
     * <p><b>Status guard:</b> only {@code RECEIVED} and {@code FAILED} events enter full processing.
     * {@code PROCESSING} (claimed by another thread/instance), {@code PROCESSED}, and
     * {@code QUARANTINED} are all skipped — this provides a second line of defence against
     * concurrent re-entry even without database-level row locking.
     *
     * <p><b>Resume safety:</b> if a previous run created the ticket but failed before marking the
     * event {@code PROCESSED}, the {@code ticketId} field is already set. The {@code CREATE_TICKET}
     * branch detects this and skips ticket creation, resuming from the document/attachment steps
     * instead. This prevents a second ticket from being created on retry.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(Long eventId) {
        EmailIngressEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IngressEventNotFoundException(eventId));

        // Only RECEIVED, FAILED, and PROCESSING events may enter full processing.
        // PROCESSING events were already claimed by claimReceivedBatch/claimFailedBatch — their
        // attempts counter was already incremented there, so we don't re-claim them here.
        // PROCESSED and QUARANTINED are terminal states and are always skipped.
        if (event.getStatus() != IngressEventStatus.RECEIVED
                && event.getStatus() != IngressEventStatus.FAILED
                && event.getStatus() != IngressEventStatus.PROCESSING) {
            log.debug("Event {} in non-processable state ({}) — skipping", eventId, event.getStatus());
            return;
        }

        if (event.getStatus() == IngressEventStatus.PROCESSING) {
            // Already claimed by claimReceivedBatch/claimFailedBatch — attempts already incremented.
            log.info("Processing ingress event {} (pre-claimed) — messageId: '{}' (attempt {})",
                    eventId, event.getMessageId(), event.getProcessingAttempts());
        } else {
            // Direct call (e.g. immediate processing after receive) — claim now.
            log.info("Processing ingress event {} — messageId: '{}' (attempt {})",
                    eventId, event.getMessageId(), event.getProcessingAttempts() + 1);
            event.setStatus(IngressEventStatus.PROCESSING);
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            event.setLastAttemptAt(Instant.now());
            eventRepository.save(event);
        }

        try {
            // 1. Loop/auto-reply detection
            if (loopDetectionService.isLoop(event.getRawSubject(), event.getRawFrom(), null)) {
                log.info("Loop/auto-reply detected — eventId: {} — ignoring", eventId);
                event.setStatus(IngressEventStatus.PROCESSED);
                event.setProcessedAt(Instant.now());
                event.setFailureReason("Loop or auto-reply detected");
                eventRepository.save(event);
                metrics.inboundIgnored();
                return;
            }

            // 2. Route the email
            RoutingResult routing = routingService.route(event);
            log.info("Routing result — eventId: {}, action: {}", eventId, routing.action());

            switch (routing.action()) {
                case IGNORE -> {
                    event.setStatus(IngressEventStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    eventRepository.save(event);
                    metrics.inboundIgnored();
                }
                case REJECT -> {
                    event.setStatus(IngressEventStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    event.setFailureReason("Rejected by policy");
                    eventRepository.save(event);
                    metrics.inboundIgnored();
                }
                case QUARANTINE -> {
                    event.setStatus(IngressEventStatus.QUARANTINED);
                    event.setFailureReason(routing.quarantineReason());
                    eventRepository.save(event);
                    metrics.inboundQuarantined();
                }
                case CREATE_TICKET -> {
                    // Resume safety: if a previous run created the ticket but failed before
                    // marking the event PROCESSED, the ticketId is already set. Reuse it.
                    Long ticketId;
                    if (event.getTicketId() != null) {
                        log.info("Event {} already has ticketId {} — skipping ticket creation, resuming remaining steps",
                                eventId, event.getTicketId());
                        ticketId = event.getTicketId();
                    } else {
                        ticketId = createTicketFromEvent(event, routing.customerId());
                    }
                    String documentId = saveEmailDocument(event, ticketId, routing.customerId());
                    saveAttachmentMetadataRecords(event, ticketId, documentId);
                    event.setTicketId(ticketId);
                    event.setStatus(IngressEventStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    eventRepository.save(event);
                    // Record inbound email as a structured history event
                    java.util.UUID newPublicId = ticketRepository.findById(ticketId)
                            .map(com.caseflow.ticket.domain.Ticket::getPublicId).orElse(null);
                    historyService.recordInboundEmailReceived(ticketId, newPublicId,
                            event.getId(), event.getRawFrom());
                    stampInboundAt(event.getMailboxId());
                    metrics.inboundProcessed();
                }
                case LINK_TO_TICKET -> {
                    String documentId = saveEmailDocument(event, routing.ticketId(), routing.customerId());
                    saveAttachmentMetadataRecords(event, routing.ticketId(), documentId);
                    event.setTicketId(routing.ticketId());
                    event.setStatus(IngressEventStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    eventRepository.save(event);
                    // Record inbound email as a structured history event
                    java.util.UUID linkedPublicId = ticketRepository.findById(routing.ticketId())
                            .map(com.caseflow.ticket.domain.Ticket::getPublicId).orElse(null);
                    historyService.recordInboundEmailReceived(routing.ticketId(), linkedPublicId,
                            event.getId(), event.getRawFrom());
                    stampInboundAt(event.getMailboxId());
                    metrics.inboundProcessed();
                    // System transition: e.g. WAITING_CUSTOMER → IN_PROGRESS, RESOLVED/CLOSED → REOPENED
                    systemTransitionService.applyInboundEmailTransition(routing.ticketId(), event.getId());
                }
            }

        } catch (Exception e) {
            log.error("Failed to process ingress event {} — {}", eventId, e.getMessage(), e);
            event.setStatus(IngressEventStatus.FAILED);
            event.setFailureReason(e.getMessage());
            eventRepository.save(event);
            metrics.inboundFailed();
        }
    }

    // ── Batch claim ───────────────────────────────────────────────────────────

    /**
     * Fetches up to {@code limit} RECEIVED events under PESSIMISTIC_WRITE + SKIP LOCKED,
     * marks each as PROCESSING (incrementing attempts), and commits before returning the IDs.
     *
     * <p>By committing before returning, the row locks are released and the caller can invoke
     * {@link #processEvent} per ID without holding any outer transaction — avoiding the
     * deadlock that would arise if the lock were still held when {@code REQUIRES_NEW}
     * tried to UPDATE the same rows.
     */
    @Override
    @Transactional
    public List<Long> claimReceivedBatch(int limit) {
        List<EmailIngressEvent> events = eventRepository.findAndLockReceived(limit);
        if (events.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>(events.size());
        for (EmailIngressEvent event : events) {
            event.setStatus(IngressEventStatus.PROCESSING);
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            event.setLastAttemptAt(Instant.now());
            eventRepository.save(event);
            ids.add(event.getId());
        }
        log.info("Claimed {} RECEIVED events for processing", ids.size());
        return ids;
    }

    /** Same isolation rationale as {@link #claimReceivedBatch}. */
    @Override
    @Transactional
    public List<Long> claimFailedBatch(int maxAttempts, int limit) {
        List<EmailIngressEvent> events = eventRepository.findAndLockFailedForRetry(maxAttempts, limit);
        if (events.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>(events.size());
        for (EmailIngressEvent event : events) {
            event.setStatus(IngressEventStatus.PROCESSING);
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);
            event.setLastAttemptAt(Instant.now());
            eventRepository.save(event);
            ids.add(event.getId());
        }
        log.info("Claimed {} FAILED events for retry (maxAttempts: {})", ids.size(), maxAttempts);
        return ids;
    }

    // ── Operator actions ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public void quarantineEvent(Long eventId, String reason) {
        EmailIngressEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IngressEventNotFoundException(eventId));
        event.setStatus(IngressEventStatus.QUARANTINED);
        event.setFailureReason(reason);
        eventRepository.save(event);
        log.info("Event {} manually quarantined — reason: {}", eventId, reason);
        metrics.inboundQuarantined();
    }

    @Override
    @Transactional
    public void releaseEvent(Long eventId) {
        EmailIngressEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IngressEventNotFoundException(eventId));
        if (event.getStatus() != IngressEventStatus.QUARANTINED) {
            log.warn("Release requested for non-quarantined event {} (status={})", eventId, event.getStatus());
            return;
        }
        event.setStatus(IngressEventStatus.RECEIVED);
        event.setFailureReason(null);
        eventRepository.save(event);
        log.info("Event {} released from quarantine — re-queued as RECEIVED", eventId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void stampInboundAt(Long mailboxId) {
        if (mailboxId == null) return;
        mailboxRepository.findById(mailboxId).ifPresent(m -> {
            m.setLastSuccessfulInboundAt(Instant.now());
            mailboxRepository.save(m);
        });
    }

    private Long createTicketFromEvent(EmailIngressEvent event, Long customerId) {
        String subject = event.getRawSubject();
        if (subject == null || subject.isBlank()) subject = "(no subject)";

        Ticket ticket = new Ticket();
        ticket.setTicketNo(String.format("TKT-%07d", ticketRepository.nextTicketSeq()));
        ticket.setSubject(subject);
        ticket.setCustomerId(customerId);

        applyCustomerDefaults(ticket, customerId);

        Ticket saved = ticketRepository.save(ticket);
        historyService.recordCreated(saved.getId(), null);
        log.info("Ticket {} created from ingress event {} — customerId: {}",
                saved.getId(), event.getId(), customerId);

        // Notify group members if a default group was applied
        if (saved.getAssignedGroupId() != null) {
            notificationService.notifyGroupTicketCreated(
                    saved.getId(), saved.getPublicId(), saved.getTicketNo(),
                    saved.getAssignedGroupId(), null);
        }

        return saved.getId();
    }

    private void applyCustomerDefaults(Ticket ticket, Long customerId) {
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(TicketPriority.MEDIUM);

        if (customerId == null) return;

        settingsRepository.findByCustomerId(customerId).ifPresent(settings -> {
            if (settings.getDefaultPriority() != null) {
                try {
                    ticket.setPriority(TicketPriority.valueOf(settings.getDefaultPriority()));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid defaultPriority '{}' in CustomerEmailSettings for customerId {} — using MEDIUM",
                            settings.getDefaultPriority(), customerId);
                }
            }
            if (settings.getDefaultGroupId() != null) {
                ticket.setAssignedGroupId(settings.getDefaultGroupId());
            }
        });
    }

    private String saveEmailDocument(EmailIngressEvent event, Long ticketId, Long customerId) {
        return documentRepository.findByMessageId(event.getMessageId())
                .map(EmailDocument::getId)
                .orElseGet(() -> {
                    EmailDocument doc = new EmailDocument();
                    doc.setMessageId(event.getMessageId());
                    doc.setInReplyTo(event.getInReplyTo());
                    doc.setReferences(event.getReferencesList());
                    doc.setFrom(event.getRawFrom());
                    doc.setSubject(event.getRawSubject());
                    doc.setTextBody(event.getTextBody());
                    doc.setHtmlBody(event.getHtmlBody());
                    doc.setSanitizedHtmlBody(sanitizeHtml(event.getHtmlBody()));
                    doc.setReceivedAt(event.getReceivedAt());
                    doc.setParsedAt(Instant.now());
                    doc.setTicketId(ticketId);
                    doc.setCustomerId(customerId);
                    doc.setMailboxId(event.getMailboxId());
                    doc.setDirection(EmailDirection.INBOUND);
                    doc.setThreadKey(threadingService.resolveThreadKey(
                            event.getInReplyTo(),
                            event.getReferencesList(),
                            event.getMessageId()));

                    // Populate attachment metadata on the email document
                    List<IngressAttachmentData> attachments = deserializeAttachments(event.getAttachmentsJson());
                    if (!attachments.isEmpty()) {
                        doc.setAttachments(attachments.stream()
                                .map(a -> {
                                    EmailDocument.AttachmentMetadata meta = new EmailDocument.AttachmentMetadata();
                                    meta.setFileName(a.fileName());
                                    meta.setObjectKey(a.objectKey());
                                    meta.setContentType(a.contentType());
                                    meta.setSize(a.size());
                                    return meta;
                                })
                                .toList());
                    }

                    EmailDocument saved = documentRepository.save(doc);
                    event.setDocumentId(saved.getId());
                    return saved.getId();
                });
    }

    /**
     * Creates JPA {@code AttachmentMetadata} records for any attachments stored during IMAP polling.
     * Idempotent: if the document already existed (saveEmailDocument short-circuited), the attachments
     * were already persisted during the original processing run.
     */
    private void saveAttachmentMetadataRecords(EmailIngressEvent event, Long ticketId, String documentId) {
        if (event.getAttachmentsJson() == null || ticketId == null) return;

        // Resolve the stable publicId so AttachmentMetadataMapper can build the
        // ticket-scoped download URL (/api/tickets/{publicId}/emails/{emailId}/…).
        java.util.UUID ticketPublicId = ticketRepository.findById(ticketId)
                .map(Ticket::getPublicId).orElse(null);

        List<IngressAttachmentData> attachments = deserializeAttachments(event.getAttachmentsJson());
        for (IngressAttachmentData attachment : attachments) {
            try {
                AttachmentMetadata meta = attachmentService.saveEmailAttachmentWithContext(
                        ticketId,
                        ticketPublicId,
                        documentId,
                        event.getId(),
                        attachment.fileName(),
                        attachment.objectKey(),
                        attachment.contentType(),
                        attachment.size());
                attachmentService.promoteAttachmentToFinalKey(meta, ticketPublicId, documentId);
            } catch (Exception e) {
                log.warn("Failed to persist/promote attachment metadata for ticketId={}, objectKey='{}' — {}",
                        ticketId, attachment.objectKey(), e.getMessage(), e);
            }
        }
    }

    private String serializeAttachments(List<IngressAttachmentData> attachments) {
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (Exception e) {
            log.warn("Failed to serialize attachments — {}", e.getMessage(), e);
            return null;
        }
    }

    private List<IngressAttachmentData> deserializeAttachments(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, ATTACHMENT_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize attachments JSON — {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Sanitizes raw inbound HTML using Jsoup's relaxed safelist.
     * Strips scripts, iframes, event handlers, and javascript: URIs while
     * preserving standard formatting (bold, links, lists, tables, images).
     * Returns null when the input is null or blank.
     */
    private static String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return null;
        return Jsoup.clean(html, Safelist.relaxed());
    }
}
