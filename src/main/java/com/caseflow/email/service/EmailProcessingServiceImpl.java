package com.caseflow.email.service;

import com.caseflow.common.exception.DuplicateEmailException;
import com.caseflow.customer.repository.ContactRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailProcessingServiceImpl implements EmailProcessingService {

    private final EmailDocumentRepository emailDocumentRepository;
    private final TicketRepository ticketRepository;
    private final ContactRepository contactRepository;
    private final AttachmentService attachmentService;
    private final TicketHistoryService ticketHistoryService;

    public EmailProcessingServiceImpl(EmailDocumentRepository emailDocumentRepository,
                                      TicketRepository ticketRepository,
                                      ContactRepository contactRepository,
                                      AttachmentService attachmentService,
                                      TicketHistoryService ticketHistoryService) {
        this.emailDocumentRepository = emailDocumentRepository;
        this.ticketRepository = ticketRepository;
        this.contactRepository = contactRepository;
        this.attachmentService = attachmentService;
        this.ticketHistoryService = ticketHistoryService;
    }

    @Override
    @Transactional
    public EmailDocument process(ParsedEmail parsedEmail) {
        // Idempotency — reject duplicate messageIds
        if (parsedEmail.getMessageId() != null) {
            emailDocumentRepository.findByMessageId(parsedEmail.getMessageId())
                    .ifPresent(existing -> {
                        throw new DuplicateEmailException(parsedEmail.getMessageId());
                    });
        }

        EmailDocument document = saveEmailDocument(parsedEmail);
        Long ticketId = resolveTicket(parsedEmail);
        document.setTicketId(ticketId);
        emailDocumentRepository.save(document);

        saveAttachmentMetadata(parsedEmail, document.getId(), ticketId);

        if (ticketId != null) {
            ticketHistoryService.record(ticketId, "EMAIL_LINKED", null,
                    "messageId=" + parsedEmail.getMessageId());
        }

        return document;
    }

    private EmailDocument saveEmailDocument(ParsedEmail parsedEmail) {
        EmailDocument document = new EmailDocument();
        document.setMessageId(parsedEmail.getMessageId());
        document.setInReplyTo(parsedEmail.getInReplyTo());
        document.setReferences(parsedEmail.getReferences());
        document.setSubject(parsedEmail.getSubject());
        document.setNormalizedSubject(parsedEmail.getNormalizedSubject());
        document.setFrom(parsedEmail.getFrom());
        document.setTo(parsedEmail.getTo());
        document.setCc(parsedEmail.getCc());
        document.setBcc(parsedEmail.getBcc());
        document.setHtmlBody(parsedEmail.getHtmlBody());
        document.setTextBody(parsedEmail.getTextBody());
        document.setReceivedAt(parsedEmail.getReceivedAt());
        document.setParsedAt(Instant.now());
        document.setThreadKey(deriveThreadKey(parsedEmail));
        return emailDocumentRepository.save(document);
    }

    private Long resolveTicket(ParsedEmail parsedEmail) {
        // 1. Try thread resolution via In-Reply-To / References headers
        Optional<Long> existing = resolveThreadToTicket(
                parsedEmail.getInReplyTo(), parsedEmail.getReferences());
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Match customer from sender email
        Long customerId = resolveCustomerId(parsedEmail.getFrom());

        // 3. Create new ticket
        return createTicketFromEmail(parsedEmail, customerId);
    }

    private Optional<Long> resolveThreadToTicket(String inReplyTo, List<String> references) {
        // Look up parent email by inReplyTo messageId
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            Optional<EmailDocument> parent = emailDocumentRepository.findByMessageId(inReplyTo);
            if (parent.isPresent() && parent.get().getTicketId() != null) {
                return Optional.of(parent.get().getTicketId());
            }
        }

        // Walk references chain
        if (references != null) {
            for (String ref : references) {
                Optional<EmailDocument> refDoc = emailDocumentRepository.findByMessageId(ref);
                if (refDoc.isPresent() && refDoc.get().getTicketId() != null) {
                    return Optional.of(refDoc.get().getTicketId());
                }
            }
        }

        return Optional.empty();
    }

    private Long resolveCustomerId(String fromEmail) {
        if (fromEmail == null) return null;
        return contactRepository.findByEmail(fromEmail)
                .map(contact -> contact.getCustomer().getId())
                .orElse(null);
    }

    private Long createTicketFromEmail(ParsedEmail parsedEmail, Long customerId) {
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setSubject(parsedEmail.getSubject() != null ? parsedEmail.getSubject() : "(no subject)");
        ticket.setStatus(TicketStatus.NEW);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCustomerId(customerId);
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordCreated(saved.getId(), null);
        return saved.getId();
    }

    private void saveAttachmentMetadata(ParsedEmail parsedEmail, String emailId, Long ticketId) {
        if (parsedEmail.getAttachments() == null || parsedEmail.getAttachments().isEmpty()) return;
        for (ParsedEmail.ParsedAttachment attachment : parsedEmail.getAttachments()) {
            attachmentService.saveMetadata(ticketId, emailId,
                    attachment.getFileName(), attachment.getObjectKey(),
                    attachment.getContentType(), attachment.getSize());
        }
    }

    private String deriveThreadKey(ParsedEmail parsedEmail) {
        // Inherit threadKey from parent email if it exists
        if (parsedEmail.getInReplyTo() != null && !parsedEmail.getInReplyTo().isBlank()) {
            return emailDocumentRepository.findByMessageId(parsedEmail.getInReplyTo())
                    .map(EmailDocument::getThreadKey)
                    .orElse(parsedEmail.getInReplyTo());
        }
        if (parsedEmail.getReferences() != null && !parsedEmail.getReferences().isEmpty()) {
            return parsedEmail.getReferences().get(0);
        }
        return parsedEmail.getMessageId() != null
                ? parsedEmail.getMessageId()
                : UUID.randomUUID().toString();
    }

    private String generateTicketNo() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
