package com.caseflow.ai.context;

import com.caseflow.ai.context.dto.ReplyDraftContext;
import com.caseflow.ai.context.dto.SimilarCasesContext;
import com.caseflow.ai.context.dto.SummaryContext;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.domain.EmailDirection;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.repository.NoteRepository;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.domain.TicketTag;
import com.caseflow.email.repository.MailTemplateRepository;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketAiContextBuilderTest {

    @Mock private NoteRepository noteRepository;
    @Mock private EmailDocumentRepository emailDocumentRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketTagRepository ticketTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private MailTemplateRepository mailTemplateRepository;

    @InjectMocks
    private TicketAiContextBuilder builder;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setTicketNo("TKT-0000001");
        ticket.setSubject("Login issue after password change");
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setPriority(TicketPriority.HIGH);
        setId(ticket, 1L);
        setCustomerId(ticket, 10L);
    }

    @Test
    void buildSummaryContext_includesCustomerName() {
        Customer customer = new Customer();
        customer.setName("Acme Corp");

        when(customerRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(List.of());
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());

        SummaryContext ctx = builder.buildSummaryContext(ticket);

        assertThat(ctx.customerName()).isEqualTo("Acme Corp");
        assertThat(ctx.ticketNo()).isEqualTo("TKT-0000001");
        assertThat(ctx.subject()).isEqualTo("Login issue after password change");
        assertThat(ctx.status()).isEqualTo("IN_PROGRESS");
        assertThat(ctx.priority()).isEqualTo("HIGH");
    }

    @Test
    void buildSummaryContext_includesTagCodes() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());
        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(List.of());
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        TicketTag tt = new TicketTag();
        tt.setTicketId(1L);
        tt.setTagId(5L);
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of(tt));

        Tag tag = new Tag();
        tag.setCode("AUTH");
        tag.setName("Authentication");
        when(tagRepository.findAllById(List.of(5L))).thenReturn(List.of(tag));

        SummaryContext ctx = builder.buildSummaryContext(ticket);

        assertThat(ctx.tags()).containsExactly("AUTH");
    }

    @Test
    void buildSummaryContext_limitsInboundMessages_toMax5() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());

        List<EmailDocument> emails = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> {
                    EmailDocument e = new EmailDocument();
                    e.setDirection(EmailDirection.INBOUND);
                    e.setBodyPreview("Message " + i);
                    e.setFrom("customer@example.com");
                    setReceivedAt(e, Instant.now().minusSeconds(100L - i));
                    return e;
                })
                .toList();
        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(emails);

        SummaryContext ctx = builder.buildSummaryContext(ticket);

        assertThat(ctx.recentInboundMessages()).hasSize(5);
    }

    @Test
    void buildSummaryContext_masksEmailAddresses() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());

        EmailDocument email = new EmailDocument();
        email.setDirection(EmailDirection.INBOUND);
        email.setFrom("john.doe@example.com");
        email.setBodyPreview("Hi, I need help");
        setReceivedAt(email, Instant.now());
        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(List.of(email));

        SummaryContext ctx = builder.buildSummaryContext(ticket);

        // Email address should be masked — not the full address
        assertThat(ctx.recentInboundMessages().get(0).from())
                .doesNotContain("john.doe")
                .contains("***@example.com");
    }

    @Test
    void buildSummaryContext_includesOnlyInternalNotes() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());
        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(List.of());
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());

        Note internalNote = new Note();
        internalNote.setContent("Internal note about the issue");
        internalNote.setType(NoteType.INTERNAL);
        setNoteCreatedAt(internalNote, Instant.now());

        Note infoNote = new Note();
        infoNote.setContent("Info note — not internal");
        infoNote.setType(NoteType.INFO);
        setNoteCreatedAt(infoNote, Instant.now());

        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(internalNote, infoNote));

        SummaryContext ctx = builder.buildSummaryContext(ticket);

        // Only internal notes should be included
        assertThat(ctx.recentInternalNotes()).containsExactly("Internal note about the issue");
    }

    @Test
    void buildReplyDraftContext_identifiesLatestInboundMessage() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());

        EmailDocument older = new EmailDocument();
        older.setDirection(EmailDirection.INBOUND);
        older.setFrom("c@example.com");
        older.setBodyPreview("First message");
        setReceivedAt(older, Instant.now().minusSeconds(200));

        EmailDocument latest = new EmailDocument();
        latest.setDirection(EmailDirection.INBOUND);
        latest.setFrom("c@example.com");
        latest.setBodyPreview("Latest message — need urgent help");
        setReceivedAt(latest, Instant.now().minusSeconds(10));

        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(List.of(older, latest));

        ReplyDraftContext ctx = builder.buildReplyDraftContext(ticket);

        assertThat(ctx.latestInboundMessage()).isEqualTo("Latest message — need urgent help");
    }

    @Test
    void buildSimilarCasesContext_includesSubjectAndTags() {
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());

        ticket.setDescription("User cannot log in after resetting their password.");

        SimilarCasesContext ctx = builder.buildSimilarCasesContext(ticket);

        assertThat(ctx.ticketNo()).isEqualTo("TKT-0000001");
        assertThat(ctx.subject()).isEqualTo("Login issue after password change");
        assertThat(ctx.problemSummary()).contains("User cannot log in");
    }

    @Test
    void buildSummaryContext_truncatesLongBodyPreview() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());
        when(noteRepository.findByTicketIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(ticketTagRepository.findByTicketId(1L)).thenReturn(List.of());
        when(tagRepository.findAllById(anyList())).thenReturn(List.of());

        // Create a preview longer than 500 chars
        String longPreview = "x".repeat(700);
        EmailDocument email = new EmailDocument();
        email.setDirection(EmailDirection.INBOUND);
        email.setFrom("c@example.com");
        email.setBodyPreview(longPreview);
        setReceivedAt(email, Instant.now());
        when(emailDocumentRepository.findByTicketId(1L)).thenReturn(List.of(email));

        SummaryContext ctx = builder.buildSummaryContext(ticket);

        // Truncated to 500 + the ellipsis character
        assertThat(ctx.recentInboundMessages().get(0).preview()).hasSize(501);
        assertThat(ctx.recentInboundMessages().get(0).preview()).endsWith("…");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void setId(Ticket ticket, Long id) {
        try {
            var f = Ticket.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(ticket, id);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void setCustomerId(Ticket ticket, Long customerId) {
        ticket.setCustomerId(customerId);
    }

    private static void setReceivedAt(EmailDocument email, Instant instant) {
        email.setReceivedAt(instant);
    }

    private static void setNoteCreatedAt(Note note, Instant instant) {
        try {
            var f = Note.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(note, instant);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
