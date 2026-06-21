package com.caseflow.ai.context;

import com.caseflow.ai.context.dto.PolicyGuidanceContext;
import com.caseflow.ai.context.dto.ReplyDraftContext;
import com.caseflow.ai.context.dto.SimilarCasesContext;
import com.caseflow.ai.context.dto.SummaryContext;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.domain.EmailDirection;
import com.caseflow.email.domain.MailTemplate;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.email.repository.MailTemplateRepository;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.repository.NoteRepository;
import com.caseflow.sla.domain.SlaState;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketTag;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assembles purpose-specific context DTOs from CaseFlow domain data for AI requests.
 *
 * <h2>Design rules</h2>
 * <ul>
 *   <li>Never dumps raw JPA entities into AI requests</li>
 *   <li>Each build method selects only the fields meaningful for that AI operation</li>
 *   <li>Truncates large text fields to prevent oversized prompts</li>
 *   <li>Limits message/note counts to the most recent N entries</li>
 *   <li>Never includes PII beyond what is necessary (e.g., full email addresses are excluded)</li>
 * </ul>
 */
@Component
public class TicketAiContextBuilder {

    private static final int MAX_MESSAGES_IN_CONTEXT = 5;
    private static final int MAX_NOTES_IN_CONTEXT = 3;
    private static final int PREVIEW_MAX_CHARS = 500;
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    private static final String DEFAULT_REPLY_TEMPLATE_CODE = "CUSTOMER_REPLY";

    private final NoteRepository noteRepository;
    private final EmailDocumentRepository emailDocumentRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final TicketTagRepository ticketTagRepository;
    private final TagRepository tagRepository;
    private final MailTemplateRepository mailTemplateRepository;

    public TicketAiContextBuilder(NoteRepository noteRepository,
                                   EmailDocumentRepository emailDocumentRepository,
                                   CustomerRepository customerRepository,
                                   UserRepository userRepository,
                                   TicketTagRepository ticketTagRepository,
                                   TagRepository tagRepository,
                                   MailTemplateRepository mailTemplateRepository) {
        this.noteRepository = noteRepository;
        this.emailDocumentRepository = emailDocumentRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.ticketTagRepository = ticketTagRepository;
        this.tagRepository = tagRepository;
        this.mailTemplateRepository = mailTemplateRepository;
    }

    // ── Public builders ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SummaryContext buildSummaryContext(Ticket ticket) {
        String customerName = resolveCustomerName(ticket.getCustomerId());
        String assignedUser = resolveUserDisplayName(ticket.getAssignedUserId());
        String assignedGroup = resolveGroupName(ticket.getAssignedGroupId());
        List<String> tags = resolveTagCodes(ticket.getId());
        String slaState = computeSlaState(ticket);

        List<EmailDocument> emails = emailDocumentRepository.findByTicketId(ticket.getId());
        List<Note> notes = noteRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        List<SummaryContext.MessageSnippet> inbound = emails.stream()
                .filter(e -> EmailDirection.INBOUND.equals(e.getDirection()))
                .sorted(Comparator.comparing(EmailDocument::getReceivedAt,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .limit(MAX_MESSAGES_IN_CONTEXT)
                .map(e -> new SummaryContext.MessageSnippet(
                        maskEmailAddress(e.getFrom()),
                        truncate(e.getBodyPreview() != null ? e.getBodyPreview() : e.getTextBody()),
                        formatInstant(e.getReceivedAt())))
                .toList();

        List<SummaryContext.MessageSnippet> outbound = emails.stream()
                .filter(e -> EmailDirection.OUTBOUND.equals(e.getDirection()))
                .sorted(Comparator.comparing(EmailDocument::getReceivedAt,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .limit(MAX_MESSAGES_IN_CONTEXT)
                .map(e -> new SummaryContext.MessageSnippet(
                        "agent",
                        truncate(e.getBodyPreview() != null ? e.getBodyPreview() : e.getTextBody()),
                        formatInstant(e.getReceivedAt())))
                .toList();

        List<String> internalNotes = notes.stream()
                .filter(n -> NoteType.INTERNAL.equals(n.getType()))
                .sorted(Comparator.comparing(Note::getCreatedAt).reversed())
                .limit(MAX_NOTES_IN_CONTEXT)
                .map(n -> truncate(n.getContent()))
                .toList();

        return new SummaryContext(
                ticket.getTicketNo(),
                ticket.getSubject(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                customerName,
                assignedUser,
                assignedGroup,
                tags,
                slaState,
                inbound,
                outbound,
                internalNotes,
                "en"
        );
    }

    @Transactional(readOnly = true)
    public ReplyDraftContext buildReplyDraftContext(Ticket ticket) {
        String customerName = resolveCustomerName(ticket.getCustomerId());
        List<String> tags = resolveTagCodes(ticket.getId());

        List<EmailDocument> emails = new java.util.ArrayList<>(
                emailDocumentRepository.findByTicketId(ticket.getId()));
        emails.sort(Comparator.comparing(EmailDocument::getReceivedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        List<Note> notes = noteRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());

        // Latest inbound message
        EmailDocument latestInbound = emails.stream()
                .filter(e -> EmailDirection.INBOUND.equals(e.getDirection()))
                .max(Comparator.comparing(EmailDocument::getReceivedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        // Thread context — last N messages ordered oldest-first
        List<ReplyDraftContext.MessageSnippet> thread = emails.stream()
                .sorted(Comparator.comparing(EmailDocument::getReceivedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .skip(Math.max(0, emails.size() - MAX_MESSAGES_IN_CONTEXT))
                .map(e -> new ReplyDraftContext.MessageSnippet(
                        e.getDirection() != null ? e.getDirection().name().toLowerCase() : "unknown",
                        truncate(e.getBodyPreview() != null ? e.getBodyPreview() : e.getTextBody()),
                        formatInstant(e.getReceivedAt())))
                .toList();

        String latestBody = latestInbound != null
                ? truncate(latestInbound.getBodyPreview() != null
                        ? latestInbound.getBodyPreview() : latestInbound.getTextBody())
                : null;
        String latestFrom = latestInbound != null ? maskEmailAddress(latestInbound.getFrom()) : null;

        List<String> internalNotes = notes.stream()
                .filter(n -> NoteType.INTERNAL.equals(n.getType()))
                .sorted(Comparator.comparing(Note::getCreatedAt).reversed())
                .limit(MAX_NOTES_IN_CONTEXT)
                .map(n -> truncate(n.getContent()))
                .toList();

        List<String> constraints = buildConstraints(ticket);
        String selectedTemplateCode = resolveReplyTemplateCode();

        return new ReplyDraftContext(
                ticket.getTicketNo(),
                ticket.getSubject(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                customerName,
                latestBody,
                latestFrom,
                thread,
                tags,
                internalNotes,
                "en",
                "professional",
                List.of(),   // policySnippets — no Policy module yet; context is ready
                constraints,
                selectedTemplateCode
        );
    }

    @Transactional(readOnly = true)
    public SimilarCasesContext buildSimilarCasesContext(Ticket ticket) {
        List<String> tags = resolveTagCodes(ticket.getId());
        String customerCategory = resolveCustomerCategory(ticket.getCustomerId());

        return new SimilarCasesContext(
                ticket.getTicketNo(),
                ticket.getSubject(),
                truncate(ticket.getDescription()),
                tags,
                customerCategory
        );
    }

    @Transactional(readOnly = true)
    public PolicyGuidanceContext buildPolicyGuidanceContext(Ticket ticket, String userQuestion) {
        List<String> tags = resolveTagCodes(ticket.getId());
        String customerName = resolveCustomerName(ticket.getCustomerId());

        return new PolicyGuidanceContext(
                ticket.getTicketNo(),
                userQuestion,
                ticket.getSubject(),
                ticket.getStatus().name(),
                tags,
                customerName,
                "en",
                List.of()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveCustomerName(Long customerId) {
        if (customerId == null) return null;
        return customerRepository.findById(customerId).map(Customer::getName).orElse(null);
    }

    private String resolveCustomerCategory(Long customerId) {
        if (customerId == null) return null;
        return customerRepository.findById(customerId).map(Customer::getCode).orElse(null);
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getFullName())
                .orElse(null);
    }

    /** Returns group name; Group is not loaded here — uses userId resolution for now. */
    private String resolveGroupName(Long groupId) {
        return null; // Populated in future when GroupRepository is injected
    }

    private List<String> resolveTagCodes(Long ticketId) {
        List<TicketTag> ticketTags = ticketTagRepository.findByTicketId(ticketId);
        if (ticketTags.isEmpty()) return List.of();
        List<Long> tagIds = ticketTags.stream().map(TicketTag::getTagId).toList();
        return tagRepository.findAllById(tagIds).stream()
                .map(Tag::getCode)
                .sorted()
                .toList();
    }

    private String computeSlaState(Ticket ticket) {
        Instant now = Instant.now();
        if (ticket.getResolutionDueAt() != null && now.isAfter(ticket.getResolutionDueAt())) {
            return SlaState.BREACHED.name();
        }
        if (ticket.getResolutionDueAt() != null) {
            long minutesLeft = java.time.Duration.between(now, ticket.getResolutionDueAt()).toMinutes();
            if (minutesLeft <= 60) return SlaState.WARNING.name();
        }
        return SlaState.OK.name();
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() <= PREVIEW_MAX_CHARS ? text : text.substring(0, PREVIEW_MAX_CHARS) + "…";
    }

    private String maskEmailAddress(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private List<String> buildConstraints(Ticket ticket) {
        List<String> constraints = new ArrayList<>();
        if (ticket.getResolutionDueAt() != null) {
            long minutesLeft = java.time.Duration.between(Instant.now(), ticket.getResolutionDueAt()).toMinutes();
            if (minutesLeft > 0) {
                constraints.add("SLA resolution deadline in " + minutesLeft + " minutes");
            } else {
                constraints.add("SLA resolution deadline already breached");
            }
        }
        String category = resolveCustomerCategory(ticket.getCustomerId());
        if (category != null) {
            constraints.add("Customer category: " + category);
        }
        return constraints;
    }

    private String resolveReplyTemplateCode() {
        return mailTemplateRepository.findByCodeAndIsActiveTrue(DEFAULT_REPLY_TEMPLATE_CODE)
                .map(MailTemplate::getCode)
                .orElse(null);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return null;
        return ISO_FMT.format(instant.atOffset(ZoneOffset.UTC));
    }
}
