package com.caseflow.common.dev;

import com.caseflow.customer.domain.Contact;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.ContactRepository;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.email.document.EmailDocument;
import com.caseflow.email.repository.EmailDocumentRepository;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.GroupType;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.repository.NoteRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.workflow.history.TicketHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Dev seed data — active only with "dev" profile. Idempotent.
 *
 * V2 credentials:
 *   alice / admin123  → ADMIN
 *   bob   / agent123  → AGENT
 *   carol / viewer123 → VIEWER
 */
@Component
@Profile("dev")
public class DevDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataLoader.class);

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ContactRepository contactRepository;
    private final TicketRepository ticketRepository;
    private final NoteRepository noteRepository;
    private final TicketHistoryService ticketHistoryService;
    private final EmailDocumentRepository emailDocumentRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataLoader(GroupRepository groupRepository,
                         UserRepository userRepository,
                         CustomerRepository customerRepository,
                         ContactRepository contactRepository,
                         TicketRepository ticketRepository,
                         NoteRepository noteRepository,
                         TicketHistoryService ticketHistoryService,
                         EmailDocumentRepository emailDocumentRepository,
                         PasswordEncoder passwordEncoder) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.contactRepository = contactRepository;
        this.ticketRepository = ticketRepository;
        this.noteRepository = noteRepository;
        this.ticketHistoryService = ticketHistoryService;
        this.emailDocumentRepository = emailDocumentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ticketRepository.count() > 0) {
            log.info("[Dev] Seed data already present — skipping.");
            return;
        }

        log.info("[Dev] Loading seed data...");

        Group support = createGroup("Support", GroupType.SUPPORT);
        Group ops     = createGroup("Operations", GroupType.OPERATIONS);

        User alice = createUser("alice", "alice@caseflow.dev", "Alice Admin",   "ADMIN",  "admin123");
        User bob   = createUser("bob",   "bob@caseflow.dev",   "Bob Agent",    "AGENT",  "agent123");
        User carol = createUser("carol", "carol@caseflow.dev", "Carol Viewer", "VIEWER", "viewer123");

        alice.getGroups().add(support);
        alice.getGroups().add(ops);
        bob.getGroups().add(support);
        userRepository.saveAll(List.of(alice, bob, carol));

        Customer acme   = createCustomer("ACME Corp",   "ACME");
        Customer globex = createCustomer("Globex Inc", "GLOBEX");

        createContact(acme,   "john.doe@acme.com",  "John Doe",  true);
        createContact(acme,   "jane.doe@acme.com",  "Jane Doe",  false);
        createContact(globex, "burns@globex.com",   "Mr Burns",  true);

        Ticket t1 = createTicket("TKT-0001", "Login page is broken",
                "Users cannot log in after the latest deploy.",
                TicketStatus.NEW, TicketPriority.CRITICAL, acme.getId());

        Ticket t2 = createTicket("TKT-0002", "Export button missing from reports",
                "The export to CSV button disappeared from the reports page.",
                TicketStatus.TRIAGED, TicketPriority.HIGH, acme.getId());

        Ticket t3 = createTicket("TKT-0003", "Slow response on dashboard",
                "Dashboard takes >10s to load.",
                TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, globex.getId());
        t3.setAssignedUserId(bob.getId());
        t3.setAssignedGroupId(support.getId());
        ticketRepository.save(t3);

        ticketHistoryService.recordCreated(t1.getId(), alice.getId());
        ticketHistoryService.recordCreated(t2.getId(), alice.getId());
        ticketHistoryService.recordCreated(t3.getId(), bob.getId());
        ticketHistoryService.recordAssigned(t3.getId(), alice.getId(), bob.getId(), support.getId());

        createNote(t1.getId(), bob.getId(), NoteType.INVESTIGATION,
                "Checked auth logs — JWT secret rotation may be the cause.");
        createNote(t3.getId(), bob.getId(), NoteType.INFO,
                "Reproduced locally. Profiling the DB query now.");

        createSampleEmail(t1.getId());

        log.info("[Dev] Seed data loaded: 2 groups, 3 users, 2 customers, 3 contacts, 3 tickets, 2 notes.");
    }

    private Group createGroup(String name, GroupType type) {
        Group g = new Group();
        g.setName(name);
        g.setType(type);
        g.setIsActive(true);
        return groupRepository.save(g);
    }

    private User createUser(String username, String email, String fullName, String role, String password) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setIsActive(true);
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(u);
    }

    private Customer createCustomer(String name, String code) {
        Customer c = new Customer();
        c.setName(name);
        c.setCode(code);
        c.setIsActive(true);
        return customerRepository.save(c);
    }

    private void createContact(Customer customer, String email, String name, boolean primary) {
        Contact c = new Contact();
        c.setCustomer(customer);
        c.setEmail(email);
        c.setName(name);
        c.setIsPrimary(primary);
        c.setIsActive(true);
        contactRepository.save(c);
    }

    private Ticket createTicket(String ticketNo, String subject, String description,
                                TicketStatus status, TicketPriority priority, Long customerId) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setSubject(subject);
        t.setDescription(description);
        t.setStatus(status);
        t.setPriority(priority);
        t.setCustomerId(customerId);
        return ticketRepository.save(t);
    }

    private void createNote(Long ticketId, Long createdBy, NoteType type, String content) {
        Note n = new Note();
        n.setTicketId(ticketId);
        n.setCreatedBy(createdBy);
        n.setType(type);
        n.setContent(content);
        noteRepository.save(n);
    }

    private void createSampleEmail(Long ticketId) {
        if (emailDocumentRepository.count() > 0) return;
        EmailDocument doc = new EmailDocument();
        doc.setMessageId("<seed-001@acme.com>");
        doc.setThreadKey("thread-seed-001");
        doc.setSubject("Login page is broken");
        doc.setFrom("john.doe@acme.com");
        doc.setTo(List.of("support@caseflow.dev"));
        doc.setTextBody("Hi, we cannot log in after your latest release. Please investigate urgently.");
        doc.setReceivedAt(Instant.now().minusSeconds(3600));
        doc.setParsedAt(Instant.now().minusSeconds(3590));
        doc.setTicketId(ticketId);
        emailDocumentRepository.save(doc);
    }
}
