package com.caseflow.email.service;

import com.caseflow.common.exception.IngressEventNotFoundException;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.domain.EmailDirection;
import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.email.repository.EmailIngressEventRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EmailIngressServiceImpl implements EmailIngressService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngressServiceImpl.class);

    private final EmailIngressEventRepository eventRepository;
    private final EmailDocumentRepository documentRepository;
    private final TicketRepository ticketRepository;
    private final EmailRoutingService routingService;
    private final EmailThreadingService threadingService;
    private final LoopDetectionService loopDetectionService;
    private final TicketHistoryService historyService;
    private final EmailMetrics metrics;

    public EmailIngressServiceImpl(EmailIngressEventRepository eventRepository,
                                   EmailDocumentRepository documentRepository,
                                   TicketRepository ticketRepository,
                                   EmailRoutingService routingService,
                                   EmailThreadingService threadingService,
                                   LoopDetectionService loopDetectionService,
                                   TicketHistoryService historyService,
                                   EmailMetrics metrics) {
        this.eventRepository = eventRepository;
        this.documentRepository = documentRepository;
        this.ticketRepository = ticketRepository;
        this.routingService = routingService;
        this.threadingService = threadingService;
        this.loopDetectionService = loopDetectionService;
        this.historyService = historyService;
        this.metrics = metrics;
    }

    // ── Stage 1 ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EmailIngressEvent receiveEvent(String messageId, String rawFrom, String rawTo,
                                          String rawSubject, Long mailboxId, Instant receivedAt) {
        // Idempotency: if event already exists, return it unchanged
        return eventRepository.findByMessageId(messageId)
                .orElseGet(() -> {
                    EmailIngressEvent event = new EmailIngressEvent();
                    event.setMessageId(messageId);
                    event.setRawFrom(rawFrom);
                    event.setRawTo(rawTo);
                    event.setRawSubject(rawSubject);
                    event.setMailboxId(mailboxId);
                    event.setReceivedAt(receivedAt != null ? receivedAt : Instant.now());
                    event.setStatus(IngressEventStatus.RECEIVED);
                    EmailIngressEvent saved = eventRepository.save(event);
                    log.info("Ingress event stored — eventId: {}, messageId: '{}'",
                            saved.getId(), messageId);
                    metrics.inboundReceived();
                    return saved;
                });
    }

    // ── Stage 2 ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void processEvent(Long eventId) {
        EmailIngressEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IngressEventNotFoundException(eventId));

        if (event.getStatus() == IngressEventStatus.PROCESSED) {
            log.debug("Event {} already processed — skipping", eventId);
            return;
        }

        log.info("Processing ingress event {} — messageId: '{}'", eventId, event.getMessageId());
        event.setStatus(IngressEventStatus.PROCESSING);
        event.setProcessingAttempts(event.getProcessingAttempts() + 1);
        event.setLastAttemptAt(Instant.now());
        eventRepository.save(event);

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
                    Long ticketId = createTicketFromEvent(event, routing.customerId());
                    saveEmailDocument(event, ticketId, routing.customerId());
                    event.setTicketId(ticketId);
                    event.setStatus(IngressEventStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    eventRepository.save(event);
                    metrics.inboundProcessed();
                }
                case LINK_TO_TICKET -> {
                    saveEmailDocument(event, routing.ticketId(), routing.customerId());
                    event.setTicketId(routing.ticketId());
                    event.setStatus(IngressEventStatus.PROCESSED);
                    event.setProcessedAt(Instant.now());
                    eventRepository.save(event);
                    historyService.record(routing.ticketId(), "EMAIL_LINKED", null,
                            "messageId=" + event.getMessageId());
                    metrics.inboundProcessed();
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

    // ── Private helpers ───────────────────────────────────────────────────────

    private Long createTicketFromEvent(EmailIngressEvent event, Long customerId) {
        String subject = event.getRawSubject();
        if (subject == null || subject.isBlank()) subject = "(no subject)";
        Ticket ticket = new Ticket();
        ticket.setTicketNo("TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setSubject(subject);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCustomerId(customerId);
        Ticket saved = ticketRepository.save(ticket);
        historyService.recordCreated(saved.getId(), null);
        log.info("Ticket {} created from ingress event {}", saved.getId(), event.getId());
        return saved.getId();
    }

    private String saveEmailDocument(EmailIngressEvent event, Long ticketId, Long customerId) {
        // Check idempotency — don't save if we already have this messageId
        return documentRepository.findByMessageId(event.getMessageId())
                .map(EmailDocument::getId)
                .orElseGet(() -> {
                    EmailDocument doc = new EmailDocument();
                    doc.setMessageId(event.getMessageId());
                    doc.setFrom(event.getRawFrom());
                    doc.setSubject(event.getRawSubject());
                    doc.setReceivedAt(event.getReceivedAt());
                    doc.setParsedAt(Instant.now());
                    doc.setTicketId(ticketId);
                    doc.setCustomerId(customerId);
                    doc.setMailboxId(event.getMailboxId());
                    doc.setDirection(EmailDirection.INBOUND);
                    doc.setThreadKey(threadingService.resolveThreadKey(null, null, event.getMessageId()));
                    EmailDocument saved = documentRepository.save(doc);
                    event.setDocumentId(saved.getId());
                    return saved.getId();
                });
    }
}
