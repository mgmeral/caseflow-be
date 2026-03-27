package com.caseflow.email.service;

import com.caseflow.customer.repository.ContactRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        EmailDocument document = saveEmailDocument(parsedEmail);

        Long ticketId = resolveTicket(parsedEmail, document);

        document.setTicketId(ticketId);
        emailDocumentRepository.save(document);

        saveAttachmentMetadata(parsedEmail, document.getId(), ticketId);

        ticketHistoryService.record(ticketId, "EMAIL_LINKED", null,
                "messageId=" + parsedEmail.getMessageId());

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
        return emailDocumentRepository.save(document);
    }

    private Long resolveTicket(ParsedEmail parsedEmail, EmailDocument document) {
        // TODO: extract ThreadResolutionService
        //       Resolution must use messageId, inReplyTo, and references — not subject alone.
        Optional<Long> existingTicketId = resolveThreadToTicket(
                parsedEmail.getMessageId(),
                parsedEmail.getInReplyTo(),
                parsedEmail.getReferences()
        );

        if (existingTicketId.isPresent()) {
            return existingTicketId.get();
        }

        // TODO: extract CustomerMatchingService
        //       Matching logic should resolve by contact email, then domain fallback.
        Long customerId = resolveCustomerId(parsedEmail.getFrom());

        return createTicketFromEmail(parsedEmail, customerId);
    }

    private Optional<Long> resolveThreadToTicket(String messageId, String inReplyTo,
                                                  List<String> references) {
        // TODO: implement ThreadResolutionService
        //       1. Search EmailDocument collection by inReplyTo matching existing messageId
        //       2. Search by references list intersection
        //       3. Return ticketId from matched document if found
        //       Do not rely on subject matching alone.
        return Optional.empty();
    }

    private Long resolveCustomerId(String fromEmail) {
        // TODO: implement CustomerMatchingService
        //       1. Look up Contact by email (exact match)
        //       2. Fall back to domain-based matching (extract domain from email)
        //       3. If no match found, return null or create unknown customer placeholder
        return contactRepository.findByEmail(fromEmail)
                .map(contact -> contact.getCustomer().getId())
                .orElse(null);
    }

    private Long createTicketFromEmail(ParsedEmail parsedEmail, Long customerId) {
        Ticket ticket = new Ticket();
        ticket.setTicketNo(generateTicketNo(parsedEmail.getMessageId()));
        ticket.setSubject(parsedEmail.getSubject() != null ? parsedEmail.getSubject() : "(no subject)");
        ticket.setStatus(com.caseflow.ticket.domain.TicketStatus.NEW);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setCustomerId(customerId);
        Ticket saved = ticketRepository.save(ticket);
        ticketHistoryService.recordCreated(saved.getId(), null);
        return saved.getId();
    }

    private void saveAttachmentMetadata(ParsedEmail parsedEmail, String emailId, Long ticketId) {
        if (parsedEmail.getAttachments() == null || parsedEmail.getAttachments().isEmpty()) {
            return;
        }
        for (ParsedEmail.ParsedAttachment attachment : parsedEmail.getAttachments()) {
            attachmentService.saveMetadata(
                    ticketId,
                    emailId,
                    attachment.getFileName(),
                    attachment.getObjectKey(),
                    attachment.getContentType(),
                    attachment.getSize()
            );
        }
    }

    private String generateTicketNo(String messageId) {
        String seed = messageId != null
                ? messageId.replaceAll("[^a-zA-Z0-9]", "")
                : java.util.UUID.randomUUID().toString().replace("-", "");
        return "TKT-" + seed.substring(0, Math.min(8, seed.length())).toUpperCase();
    }
}
