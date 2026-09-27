package com.caseflow.email.service;

import com.caseflow.email.domain.EmailIngressEvent;
import com.caseflow.email.domain.IngressEventStatus;
import com.caseflow.email.repository.EmailIngressEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the ingress event admin filters (status IN, case-insensitive LIKE with escaping,
 * numeric ticket match, date range) and per-status counts against real PostgreSQL.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration")
@Import(IngressEventAdminService.class)
class IngressEventAdminServiceIntegrationTest {

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
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @MockBean private EmailIngressService ingressService;
    @Autowired private EmailIngressEventRepository repository;
    @Autowired private IngressEventAdminService service;

    private static final PageRequest NEWEST_FIRST = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "receivedAt"));

    @BeforeEach
    void seed() {
        repository.deleteAll();
        save("<a@m>", "Ali.Veli@ACME.com", "Refund request", IngressEventStatus.FAILED, null, "2026-09-01T10:00:00Z");
        save("<b@m>", "ops@globex.io", "100% discount?", IngressEventStatus.QUARANTINED, null, "2026-09-10T10:00:00Z");
        save("<c@m>", "ops@globex.io", "Invoice", IngressEventStatus.PROCESSED, 4242L, "2026-09-20T10:00:00Z");
        save("<d@m>", "x@y.io", "Hello", IngressEventStatus.PROCESSED, null, "2026-09-25T10:00:00Z");
    }

    private void save(String messageId, String from, String subject, IngressEventStatus status,
                      Long ticketId, String receivedAt) {
        EmailIngressEvent e = new EmailIngressEvent();
        e.setMessageId(messageId);
        e.setRawFrom(from);
        e.setRawSubject(subject);
        e.setStatus(status);
        e.setTicketId(ticketId);
        e.setReceivedAt(Instant.parse(receivedAt));
        repository.save(e);
    }

    private List<String> find(IngressEventFilter filter) {
        return service.findFiltered(filter, NEWEST_FIRST).map(EmailIngressEvent::getMessageId).getContent();
    }

    private static IngressEventFilter filter(List<IngressEventStatus> statuses, String q, String from, String to) {
        return new IngressEventFilter(statuses, null, null, null, q,
                from == null ? null : Instant.parse(from), to == null ? null : Instant.parse(to));
    }

    @Test
    void filtersByAnyOfSeveralStatuses_newestFirst() {
        assertThat(find(filter(List.of(IngressEventStatus.FAILED, IngressEventStatus.QUARANTINED), null, null, null)))
                .containsExactly("<b@m>", "<a@m>");
        assertThat(find(filter(List.of(), null, null, null))).hasSize(4);
    }

    @Test
    void searchesSenderSubjectAndMessageIdIgnoringCase() {
        assertThat(find(filter(null, "acme", null, null))).containsExactly("<a@m>");
        assertThat(find(filter(null, "REFUND", null, null))).containsExactly("<a@m>");
        assertThat(find(filter(null, "<c@", null, null))).containsExactly("<c@m>");
    }

    @Test
    void treatsLikeWildcardsLiterally() {
        assertThat(find(filter(null, "100%", null, null))).containsExactly("<b@m>");
        assertThat(find(filter(null, "%", null, null))).containsExactly("<b@m>");
    }

    @Test
    void numericSearchAlsoMatchesTheLinkedTicket() {
        assertThat(find(filter(null, "4242", null, null))).containsExactly("<c@m>");
    }

    @Test
    void filtersByReceivedDateRange() {
        assertThat(find(filter(null, null, "2026-09-05T00:00:00Z", "2026-09-21T00:00:00Z")))
                .containsExactly("<c@m>", "<b@m>");
    }

    @Test
    void countsEveryStatus_ignoringTheStatusFilter() {
        var counts = service.countByStatus(filter(List.of(IngressEventStatus.FAILED), "globex", null, null));

        assertThat(counts).containsEntry(IngressEventStatus.QUARANTINED, 1L)
                .containsEntry(IngressEventStatus.PROCESSED, 1L)
                .containsEntry(IngressEventStatus.FAILED, 0L)
                .containsEntry(IngressEventStatus.RECEIVED, 0L)
                .containsEntry(IngressEventStatus.PROCESSING, 0L);
    }
}
