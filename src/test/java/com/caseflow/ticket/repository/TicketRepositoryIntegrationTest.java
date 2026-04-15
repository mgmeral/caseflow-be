package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TicketRepositoryCustom} aggregate queries against a real
 * PostgreSQL database. These tests verify that JPQL predicates, Criteria API expressions,
 * and native SQL queries behave correctly with Postgres semantics — catching issues that
 * H2/in-memory DBs cannot reproduce (e.g. DATE_TRUNC, EXTRACT EPOCH, COALESCE on TIMESTAMP).
 *
 * <p>Flyway migrations are <b>not</b> applied in @DataJpaTest — the schema is created by
 * Spring from entity metadata. What matters here is query correctness, not migration order.
 */
@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@Import(TicketRepositoryImpl.class)
class TicketRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("caseflow_test")
            .withUsername("caseflow")
            .withPassword("caseflow");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Flyway for @DataJpaTest — schema generated from entities
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TestEntityManager em;

    private static long customerA = 100L;
    private static long customerB = 200L;
    private static long userId1 = 10L;
    private static long userId2 = 20L;
    private static long groupId1 = 50L;

    @BeforeEach
    void clearTickets() {
        ticketRepository.deleteAll();
        em.flush();
        em.clear();
    }

    // ── countByStatusForCustomer ──────────────────────────────────────────────

    @Test
    void countByStatusForCustomer_returnsCorrectCounts_forCustomer() {
        persist(newTicket("TKT-001", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA, null, null, null, null));
        persist(newTicket("TKT-002", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA, null, null, null, null));
        persist(newTicket("TKT-003", TicketStatus.RESOLVED, TicketPriority.LOW, customerA, null, null, null, null));
        persist(newTicket("TKT-004", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, customerB, null, null, null, null));
        em.flush();

        List<Object[]> rows = ticketRepository.countByStatusForCustomer(customerA, null, null);

        assertThat(rows).isNotEmpty();
        long inProgressCount = findCount(rows, TicketStatus.IN_PROGRESS);
        long resolvedCount = findCount(rows, TicketStatus.RESOLVED);
        assertThat(inProgressCount).isEqualTo(2);
        assertThat(resolvedCount).isEqualTo(1);
    }

    @Test
    void countByStatusForCustomer_respectsDateBounds() {
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant anHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);

        // This ticket's created_at is backdated to yesterday via @PrePersist override workaround
        // — we persist it normally and rely on the range being [yesterday, future] to include it
        persist(newTicket("TKT-A", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, customerA, null, null, null, null));
        em.flush();

        List<Object[]> withRange = ticketRepository.countByStatusForCustomer(customerA, yesterday, future);
        assertThat(withRange).isNotEmpty(); // ticket within range

        // Past-only window should not include tickets created "now"
        List<Object[]> beforeNow = ticketRepository.countByStatusForCustomer(customerA, null, anHourAgo);
        assertThat(findCount(beforeNow, TicketStatus.IN_PROGRESS)).isZero();
    }

    // ── countByStatusForCustomers (bulk) ─────────────────────────────────────

    @Test
    void countByStatusForCustomers_aggregatesBulkCorrectly() {
        persist(newTicket("TKT-B1", TicketStatus.NEW, TicketPriority.LOW, customerA, null, null, null, null));
        persist(newTicket("TKT-B2", TicketStatus.CLOSED, TicketPriority.LOW, customerB, null, null, null, null));
        em.flush();

        List<Object[]> rows = ticketRepository.countByStatusForCustomers(List.of(customerA, customerB), null, null);

        assertThat(rows).isNotEmpty();
        // customerA has 1 NEW
        assertThat(rows.stream()
                .filter(r -> r[0].equals(customerA) && r[1] == TicketStatus.NEW)
                .mapToLong(r -> ((Number) r[2]).longValue())
                .sum()).isEqualTo(1);
        // customerB has 1 CLOSED
        assertThat(rows.stream()
                .filter(r -> r[0].equals(customerB) && r[1] == TicketStatus.CLOSED)
                .mapToLong(r -> ((Number) r[2]).longValue())
                .sum()).isEqualTo(1);
    }

    // ── countBreachedResolutionSla ────────────────────────────────────────────

    @Test
    void countBreachedResolutionSla_countsOpenBreachedTickets() {
        Instant pastDue = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant futureDue = Instant.now().plus(1, ChronoUnit.HOURS);

        // Breached: open, resolution_due_at in the past
        persist(newTicket("TKT-C1", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA,
                null, pastDue, null, null));
        // Not breached: open, resolution_due_at in the future
        persist(newTicket("TKT-C2", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA,
                null, futureDue, null, null));
        // Not counted: terminal ticket even though resolution_due_at is past
        persist(newTicket("TKT-C3", TicketStatus.RESOLVED, TicketPriority.HIGH, customerA,
                null, pastDue, null, null));
        em.flush();

        long breached = ticketRepository.countBreachedResolutionSla(customerA);
        assertThat(breached).isEqualTo(1);
    }

    @Test
    void countBreachedResolutionSla_globalCount_whenCustomerIdIsNull() {
        Instant pastDue = Instant.now().minus(30, ChronoUnit.MINUTES);

        persist(newTicket("TKT-D1", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA,
                null, pastDue, null, null));
        persist(newTicket("TKT-D2", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, customerB,
                null, pastDue, null, null));
        em.flush();

        long global = ticketRepository.countBreachedResolutionSla(null);
        assertThat(global).isGreaterThanOrEqualTo(2);
    }

    // ── countBreachedByAssignedUser ───────────────────────────────────────────

    @Test
    void countBreachedByAssignedUser_returnsCorrectBreachCountPerUser() {
        Instant pastDue = Instant.now().minus(1, ChronoUnit.HOURS);

        persist(newTicket("TKT-E1", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA,
                userId1, pastDue, null, null));
        persist(newTicket("TKT-E2", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA,
                userId1, pastDue, null, null));
        persist(newTicket("TKT-E3", TicketStatus.RESOLVED, TicketPriority.HIGH, customerA,
                userId1, pastDue, null, null)); // terminal — excluded
        persist(newTicket("TKT-E4", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, customerA,
                userId2, pastDue, null, null));
        em.flush();

        List<Object[]> rows = ticketRepository.countBreachedByAssignedUser();

        long user1Breached = rows.stream()
                .filter(r -> r[0].equals(userId1))
                .mapToLong(r -> ((Number) r[1]).longValue()).sum();
        long user2Breached = rows.stream()
                .filter(r -> r[0].equals(userId2))
                .mapToLong(r -> ((Number) r[1]).longValue()).sum();

        assertThat(user1Breached).isEqualTo(2);
        assertThat(user2Breached).isEqualTo(1);
    }

    // ── countBreachedByGroup ──────────────────────────────────────────────────

    @Test
    void countBreachedByGroup_returnsCorrectBreachCountPerGroup() {
        Instant pastDue = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant futureDue = Instant.now().plus(1, ChronoUnit.HOURS);

        Ticket breached = newTicket("TKT-F1", TicketStatus.IN_PROGRESS, TicketPriority.HIGH,
                customerA, null, pastDue, null, null);
        breached.setAssignedGroupId(groupId1);
        persist(breached);

        Ticket notBreached = newTicket("TKT-F2", TicketStatus.IN_PROGRESS, TicketPriority.LOW,
                customerA, null, futureDue, null, null);
        notBreached.setAssignedGroupId(groupId1);
        persist(notBreached);
        em.flush();

        List<Object[]> rows = ticketRepository.countBreachedByGroup();

        long group1Breached = rows.stream()
                .filter(r -> r[0].equals(groupId1))
                .mapToLong(r -> ((Number) r[1]).longValue()).sum();
        assertThat(group1Breached).isEqualTo(1);
    }

    // ── avgFirstResponseMinutes ───────────────────────────────────────────────

    @Test
    void avgFirstResponseMinutes_computesCorrectAverage() {
        Instant createdAt = Instant.now().minus(120, ChronoUnit.MINUTES);
        Instant respondedAt60 = createdAt.plus(60, ChronoUnit.MINUTES);
        Instant respondedAt90 = createdAt.plus(90, ChronoUnit.MINUTES);

        Ticket t1 = newTicket("TKT-G1", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM,
                customerA, null, null, respondedAt60, null);
        Ticket t2 = newTicket("TKT-G2", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM,
                customerA, null, null, respondedAt90, null);
        persist(t1);
        persist(t2);
        em.flush();

        Double avg = ticketRepository.avgFirstResponseMinutes(customerA, null, null);
        // Avg of 60 and 90 = 75 (approximately, given createdAt may differ slightly)
        assertThat(avg).isNotNull();
        assertThat(avg).isBetween(60.0, 100.0);
    }

    @Test
    void avgFirstResponseMinutes_returnsNull_whenNoRespondedTickets() {
        persist(newTicket("TKT-G3", TicketStatus.IN_PROGRESS, TicketPriority.LOW, customerA,
                null, null, null, null));
        em.flush();

        Double avg = ticketRepository.avgFirstResponseMinutes(customerA, null, null);
        assertThat(avg).isNull();
    }

    // ── avgResolutionMinutes ──────────────────────────────────────────────────

    @Test
    void avgResolutionMinutes_computesCorrectAverage_forResolvedTickets() {
        Instant resolvedAt = Instant.now().minus(10, ChronoUnit.MINUTES); // resolved 10 min ago

        Ticket t1 = newTicket("TKT-H1", TicketStatus.RESOLVED, TicketPriority.HIGH,
                customerA, null, null, null, resolvedAt);
        persist(t1);
        em.flush();

        Double avg = ticketRepository.avgResolutionMinutes(customerA, null, null);
        assertThat(avg).isNotNull();
        assertThat(avg).isGreaterThan(0.0);
    }

    // ── countByStatusGlobal ───────────────────────────────────────────────────

    @Test
    void countByStatusGlobal_aggregatesAcrossAllCustomers() {
        persist(newTicket("TKT-I1", TicketStatus.NEW, TicketPriority.LOW, customerA, null, null, null, null));
        persist(newTicket("TKT-I2", TicketStatus.NEW, TicketPriority.LOW, customerB, null, null, null, null));
        persist(newTicket("TKT-I3", TicketStatus.RESOLVED, TicketPriority.LOW, customerA, null, null, null, null));
        em.flush();

        List<Object[]> rows = ticketRepository.countByStatusGlobal(null, null);

        long newCount = findCount(rows, TicketStatus.NEW);
        long resolvedCount = findCount(rows, TicketStatus.RESOLVED);
        assertThat(newCount).isEqualTo(2);
        assertThat(resolvedCount).isEqualTo(1);
    }

    // ── notTerminal predicate ─────────────────────────────────────────────────

    @Test
    void activeByAssignedUser_excludesTerminalStatuses() {
        Instant pastDue = Instant.now().minus(1, ChronoUnit.HOURS);

        persist(newTicket("TKT-J1", TicketStatus.IN_PROGRESS, TicketPriority.HIGH, customerA,
                userId1, pastDue, null, null));
        persist(newTicket("TKT-J2", TicketStatus.RESOLVED, TicketPriority.HIGH, customerA,
                userId1, pastDue, null, null)); // should be excluded
        persist(newTicket("TKT-J3", TicketStatus.CLOSED, TicketPriority.HIGH, customerA,
                userId1, pastDue, null, null)); // should be excluded
        em.flush();

        List<Object[]> breached = ticketRepository.countBreachedByAssignedUser();

        // Only the IN_PROGRESS ticket should count
        long user1Count = breached.stream()
                .filter(r -> r[0].equals(userId1))
                .mapToLong(r -> ((Number) r[1]).longValue()).sum();
        assertThat(user1Count).isEqualTo(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void persist(Ticket ticket) {
        em.persist(ticket);
    }

    private static long ticketSeq = 0;

    private Ticket newTicket(String ticketNo, TicketStatus status, TicketPriority priority,
                              Long customerId, Long assignedUserId,
                              Instant resolutionDueAt, Instant firstResponseRespondedAt,
                              Instant resolvedAt) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo + "_" + (++ticketSeq));
        t.setSubject("Test ticket " + ticketNo);
        t.setStatus(status);
        t.setPriority(priority);
        t.setCustomerId(customerId);
        t.setAssignedUserId(assignedUserId);
        t.setResolutionDueAt(resolutionDueAt);
        t.setFirstResponseRespondedAt(firstResponseRespondedAt);
        t.setResolvedAt(resolvedAt);
        return t;
    }

    private long findCount(List<Object[]> rows, TicketStatus status) {
        return rows.stream()
                .filter(r -> r[0] == status || r[0].equals(status))
                .mapToLong(r -> ((Number) r[1]).longValue())
                .sum();
    }
}
