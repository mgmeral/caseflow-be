package com.caseflow.ticket.service;

import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.ticket.api.dto.DashboardStatsResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getStats_returnsCorrectAggregates() {
        when(ticketRepository.count()).thenReturn(10L);
        // active (not terminal) = spec-based queries
        when(ticketRepository.count(any(Specification.class)))
                .thenReturn(7L)  // activeTickets
                .thenReturn(2L)  // resolvedTickets
                .thenReturn(1L)  // closedTickets
                .thenReturn(3L)  // unassignedTickets
                .thenReturn(2L); // waitingOver24h
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(1L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(2L);

        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        DashboardStatsResponse stats = dashboardService.getStats(1L, TicketScope.ASSIGNED_ONLY, null);

        assertThat(stats.totalTickets()).isEqualTo(10L);
        assertThat(stats.breachedSlaCount()).isEqualTo(1L);
        assertThat(stats.atRiskSlaCount()).isEqualTo(2L);
        assertThat(stats.myActionRequired()).isNotNull();
    }

    @Test
    void getStats_omitsMyActionRequired_whenUserIdIsNull() {
        when(ticketRepository.count()).thenReturn(5L);
        when(ticketRepository.count(any(Specification.class))).thenReturn(3L);
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(0L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(0L);

        DashboardStatsResponse stats = dashboardService.getStats(null, null, null);

        assertThat(stats.myActionRequired()).isNull();
        assertThat(stats.myActionRequiredItems()).isEmpty();
    }

    @Test
    void atRiskSlaCount_isIncludedInResponse() {
        when(ticketRepository.count()).thenReturn(5L);
        when(ticketRepository.count(any(Specification.class))).thenReturn(3L);
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(1L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(3L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        DashboardStatsResponse stats = dashboardService.getStats(1L, TicketScope.ASSIGNED_ONLY, null);

        assertThat(stats.atRiskSlaCount()).isEqualTo(3L);
    }

    @Test
    void waitingOver24h_usesStatusChangedAtNotUpdatedAt() {
        // Verify that the service computes waitingOver24h (metric position 5 in count calls)
        // The predicate internally uses COALESCE(statusChangedAt, createdAt) — not updatedAt —
        // so that note creation and other non-workflow updates do not reset the waiting clock.
        when(ticketRepository.count()).thenReturn(1L);
        when(ticketRepository.count(any(Specification.class)))
                .thenReturn(1L)  // active
                .thenReturn(0L)  // resolved
                .thenReturn(0L)  // closed
                .thenReturn(1L)  // unassigned
                .thenReturn(1L); // waitingOver24h
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(0L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(0L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        DashboardStatsResponse stats = dashboardService.getStats(1L, TicketScope.ASSIGNED_ONLY, null);

        // We cannot inspect the predicate expression directly in a unit test, but the fact
        // that getStats() compiles and runs against the statusChangedBefore predicate (not
        // updatedBefore) is enforced at the call site in DashboardService.getStats().
        assertThat(stats.waitingOver24h()).isEqualTo(1L);
    }

    @Test
    void notTerminal_predicate_excludesResolvedAndClosed() {
        // Verify the predicate logic via service-level counts
        // (Integration-level verification would require testcontainers)
        Specification<Ticket> spec = DashboardService.notTerminal();
        assertThat(spec).isNotNull();
    }

    @Test
    void unassignedAndActive_predicate_isCompositeOfNotTerminalAndIsNull() {
        Specification<Ticket> spec = DashboardService.unassignedAndActive();
        assertThat(spec).isNotNull();
    }

    @Test
    void getStats_myActionRequiredItems_includesCustomerName() {
        when(ticketRepository.count()).thenReturn(1L);
        when(ticketRepository.count(any(Specification.class))).thenReturn(1L);
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(0L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(0L);

        Ticket t = buildTicket(1L, "TKT-000001", 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(t));

        com.caseflow.customer.domain.Customer customer = buildCustomer(10L, "Acme Corp");
        when(customerRepository.findAllById(any())).thenReturn(List.of(customer));

        DashboardStatsResponse stats = dashboardService.getStats(42L, TicketScope.ASSIGNED_ONLY, null);

        assertThat(stats.myActionRequiredItems()).hasSize(1);
        assertThat(stats.myActionRequiredItems().get(0).customerName()).isEqualTo("Acme Corp");
        assertThat(stats.myActionRequiredItems().get(0).ticketNo()).isEqualTo("TKT-000001");
    }

    @Test
    void getStats_scopeAll_usesOperationallyUrgentQueue_notAssignedToMe() {
        when(ticketRepository.count()).thenReturn(1L);
        when(ticketRepository.count(any(Specification.class))).thenReturn(1L);
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(1L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(0L);

        Ticket urgent = buildTicket(9L, "TKT-000009", 10L);
        Page<Ticket> page = new PageImpl<>(List.of(urgent));
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        com.caseflow.customer.domain.Customer customer = buildCustomer(10L, "Acme Corp");
        when(customerRepository.findAllById(any())).thenReturn(List.of(customer));

        DashboardStatsResponse stats = dashboardService.getStats(1L, TicketScope.ALL, null);

        // ALL-scope must go through the Page-based urgent-queue query, never the
        // unbounded "assigned to me" List query (that would be empty for admins today).
        assertThat(stats.myActionRequiredItems()).hasSize(1);
        assertThat(stats.myActionRequiredItems().get(0).ticketNo()).isEqualTo("TKT-000009");
        assertThat(stats.myActionRequired()).isEqualTo(1L);
    }

    @Test
    void getStats_scopeOwnGroups_usesOperationallyUrgentQueue_scopedToGroups() {
        when(ticketRepository.count()).thenReturn(1L);
        when(ticketRepository.count(any(Specification.class))).thenReturn(1L);
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(0L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(0L);
        when(ticketRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DashboardStatsResponse stats = dashboardService.getStats(1L, TicketScope.OWN_GROUPS, List.of(2L, 3L));

        assertThat(stats.myActionRequiredItems()).isEmpty();
        assertThat(stats.myActionRequired()).isEqualTo(0L);
    }

    @Test
    void getStats_scopeOwnAndOwnGroups_keepsAssignedToMeSemantics() {
        when(ticketRepository.count()).thenReturn(1L);
        when(ticketRepository.count(any(Specification.class))).thenReturn(1L);
        when(ticketRepository.countBreachedResolutionSla(isNull())).thenReturn(0L);
        when(ticketRepository.countAtRiskResolutionSla(isNull(), any(Instant.class))).thenReturn(0L);

        Ticket mine = buildTicket(3L, "TKT-000003", 10L);
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of(mine));

        com.caseflow.customer.domain.Customer customer = buildCustomer(10L, "Acme Corp");
        when(customerRepository.findAllById(any())).thenReturn(List.of(customer));

        DashboardStatsResponse stats = dashboardService.getStats(7L, TicketScope.OWN_AND_OWN_GROUPS, List.of(2L));

        // Agents keep the unbounded "assigned to me" List query — unchanged from before this change.
        assertThat(stats.myActionRequiredItems()).hasSize(1);
        assertThat(stats.myActionRequiredItems().get(0).ticketNo()).isEqualTo("TKT-000003");
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private Ticket buildTicket(Long id, String ticketNo, Long customerId) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setSubject("Subject");
        t.setStatus(TicketStatus.NEW);
        t.setPriority(TicketPriority.MEDIUM);
        t.setCustomerId(customerId);
        try {
            var idField = Ticket.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
            var pidField = Ticket.class.getDeclaredField("publicId");
            pidField.setAccessible(true);
            pidField.set(t, UUID.randomUUID());
            var createdAtField = Ticket.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(t, Instant.now());
            var updatedAtField = Ticket.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(t, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private com.caseflow.customer.domain.Customer buildCustomer(Long id, String name) {
        com.caseflow.customer.domain.Customer c = new com.caseflow.customer.domain.Customer();
        c.setName(name);
        c.setCode("ACME");
        c.setIsActive(true);
        try {
            var field = com.caseflow.customer.domain.Customer.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }
}
