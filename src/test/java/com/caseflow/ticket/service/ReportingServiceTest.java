package com.caseflow.ticket.service;

import com.caseflow.common.exception.InvalidDateRangeException;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.api.dto.AgingBucketsResponse;
import com.caseflow.ticket.api.dto.CustomerHealthSummary;
import com.caseflow.ticket.api.dto.CustomerTicketReportResponse;
import com.caseflow.ticket.api.dto.TrendDataPoint;
import com.caseflow.ticket.api.dto.WorkloadSummaryResponse;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import com.caseflow.workflow.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock CustomerRepository customerRepository;
    @Mock TagRepository tagRepository;
    @Mock TicketTagRepository ticketTagRepository;
    @Mock UserRepository userRepository;
    @Mock GroupRepository groupRepository;
    @Mock TransferRepository transferRepository;

    @InjectMocks ReportingService reportingService;

    @Test
    void customerReport_correctStatusBuckets() {
        Customer customer = customer(1L, "Acme Corp");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end   = Instant.parse("2026-12-31T23:59:59Z");

        // DB query returns aggregated (status, count) pairs — no ticket rows loaded
        List<Object[]> counts = statusCounts(
                row(TicketStatus.NEW,              1L),
                row(TicketStatus.IN_PROGRESS,      1L),
                row(TicketStatus.WAITING_CUSTOMER, 1L),
                row(TicketStatus.RESOLVED,         1L),
                row(TicketStatus.CLOSED,           1L)
        );
        when(ticketRepository.countByStatusForCustomer(eq(1L), any(), any())).thenReturn(counts);
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), any(), any()))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, start, end);

        assertThat(report.totalCount()).isEqualTo(5);
        assertThat(report.newCount()).isEqualTo(1);
        assertThat(report.inProgressCount()).isEqualTo(1);
        assertThat(report.waitingCustomerCount()).isEqualTo(1);
        assertThat(report.resolvedCount()).isEqualTo(1);
        assertThat(report.closedCount()).isEqualTo(1);
        // openCount = NEW + IN_PROGRESS + WAITING_CUSTOMER = 3
        assertThat(report.openCount()).isEqualTo(3);
    }

    @Test
    void customerReport_dateRangeFilteredByQuery() {
        Customer customer = customer(1L, "Acme");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end   = Instant.parse("2026-01-31T23:59:59Z");

        // DB handles date range filtering — only the in-range ticket appears in the result
        when(ticketRepository.countByStatusForCustomer(eq(1L), eq(start), eq(end)))
                .thenReturn(statusCounts(row(TicketStatus.NEW, 1L)));
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), eq(start), eq(end)))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, start, end);

        assertThat(report.totalCount()).isEqualTo(1);
        assertThat(report.newCount()).isEqualTo(1);
        assertThat(report.openCount()).isEqualTo(1);
    }

    @Test
    void customerReport_openCountExcludesFinalStatuses() {
        Customer customer = customer(1L, "Acme");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        when(ticketRepository.countByStatusForCustomer(eq(1L), any(), any())).thenReturn(statusCounts(
                row(TicketStatus.NEW,      2L),
                row(TicketStatus.RESOLVED, 3L),
                row(TicketStatus.CLOSED,   1L),
                row(TicketStatus.REOPENED, 1L)
        ));
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), any(), any()))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, null, null);

        assertThat(report.totalCount()).isEqualTo(7);
        // RESOLVED and CLOSED are not open
        assertThat(report.openCount()).isEqualTo(3); // NEW(2) + REOPENED(1)
        assertThat(report.reopenedCount()).isEqualTo(1);
    }

    @Test
    void adminAggregateReport_usesBulkQueryNotPerCustomer() {
        Customer c1 = customer(1L, "Acme");
        Customer c2 = customer(2L, "Globex");
        Page<Customer> page = new PageImpl<>(List.of(c1, c2), PageRequest.of(0, 20), 2);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Single bulk query returns data for both customers — no N+1
        when(ticketRepository.countByStatusForCustomers(eq(List.of(1L, 2L)), any(), any()))
                .thenReturn(statusCounts(
                        row3(1L, TicketStatus.NEW,         3L),
                        row3(1L, TicketStatus.CLOSED,      1L),
                        row3(2L, TicketStatus.IN_PROGRESS, 5L)
                ));

        Page<AdminCustomerReportRow> result =
                reportingService.adminAggregateReport(null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
        AdminCustomerReportRow acme   = result.getContent().get(0);
        AdminCustomerReportRow globex = result.getContent().get(1);

        assertThat(acme.customerName()).isEqualTo("Acme");
        assertThat(acme.totalCount()).isEqualTo(4);   // NEW(3) + CLOSED(1)
        assertThat(acme.newCount()).isEqualTo(3);
        assertThat(acme.openCount()).isEqualTo(3);    // only NEW is open
        assertThat(acme.closedCount()).isEqualTo(1);

        assertThat(globex.customerName()).isEqualTo("Globex");
        assertThat(globex.totalCount()).isEqualTo(5);
        assertThat(globex.inProgressCount()).isEqualTo(5);
        assertThat(globex.openCount()).isEqualTo(5);
    }

    @Test
    void adminAggregateReport_emptyPage_returnsEmptyResult() {
        Page<Customer> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        Page<AdminCustomerReportRow> result =
                reportingService.adminAggregateReport(null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void customerReport_fromOnly_noBound_works() {
        Customer customer = customer(1L, "Acme");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        when(ticketRepository.countByStatusForCustomer(eq(1L), eq(start), eq(null)))
                .thenReturn(statusCounts(row(TicketStatus.NEW, 2L)));
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), eq(start), eq(null)))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, start, null);

        assertThat(report.totalCount()).isEqualTo(2);
        assertThat(report.newCount()).isEqualTo(2);
    }

    @Test
    void customerReport_toOnly_noBound_works() {
        Customer customer = customer(1L, "Acme");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Instant end = Instant.parse("2026-03-31T23:59:59Z");
        when(ticketRepository.countByStatusForCustomer(eq(1L), eq(null), eq(end)))
                .thenReturn(statusCounts(row(TicketStatus.CLOSED, 4L)));
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), eq(null), eq(end)))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, null, end);

        assertThat(report.totalCount()).isEqualTo(4);
        assertThat(report.closedCount()).isEqualTo(4);
        assertThat(report.openCount()).isZero();
    }

    @Test
    void customerReport_noTickets_returnsZeroCounts() {
        Customer customer = customer(1L, "Empty Corp");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        when(ticketRepository.countByStatusForCustomer(eq(1L), any(), any()))
                .thenReturn(new ArrayList<>());
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), any(), any()))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, null, null);

        assertThat(report.totalCount()).isZero();
        assertThat(report.openCount()).isZero();
        assertThat(report.newCount()).isZero();
        assertThat(report.inProgressCount()).isZero();
        assertThat(report.waitingCustomerCount()).isZero();
        assertThat(report.resolvedCount()).isZero();
        assertThat(report.closedCount()).isZero();
        assertThat(report.reopenedCount()).isZero();
        assertThat(report.byTag()).isEmpty();
    }

    @Test
    void customerReport_tagBreakdown_includesTagColor() {
        Customer customer = customer(1L, "Acme Corp");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(ticketRepository.countByStatusForCustomer(eq(1L), any(), any()))
                .thenReturn(new ArrayList<>());

        // countTagsByCustomerAndDateRange returns (tagId, count) pairs
        List<Object[]> tagCounts = statusCounts(new Object[]{10L, 3L});
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), any(), any()))
                .thenReturn(tagCounts);

        Tag tag = new Tag();
        tag.setCode("BUG");
        tag.setName("Bug");
        tag.setColor("#FF5733");
        setTagId(tag, 10L);
        when(tagRepository.findAllById(List.of(10L))).thenReturn(List.of(tag));

        CustomerTicketReportResponse report = reportingService.customerReport(1L, null, null);

        assertThat(report.byTag()).hasSize(1);
        CustomerTicketReportResponse.TagCount tc = report.byTag().get(0);
        assertThat(tc.tagId()).isEqualTo(10L);
        assertThat(tc.tagCode()).isEqualTo("BUG");
        assertThat(tc.tagColor()).isEqualTo("#FF5733");
        assertThat(tc.count()).isEqualTo(3L);
    }

    @Test
    void adminAggregateReport_includesCustomerColorHex() {
        Customer c = customer(1L, "Acme");
        c.setColorHex("#3B82F6");
        Page<Customer> page = new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(ticketRepository.countByStatusForCustomers(eq(List.of(1L)), any(), any()))
                .thenReturn(new ArrayList<>());

        Page<AdminCustomerReportRow> result =
                reportingService.adminAggregateReport(null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).customerColorHex()).isEqualTo("#3B82F6");
    }

    // ── Date range validation ─────────────────────────────────────────────────

    @Test
    void customerReport_throwsInvalidDateRange_whenFromAfterTo() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> reportingService.customerReport(1L, from, to))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("dateFrom");
    }

    @Test
    void adminAggregateReport_throwsInvalidDateRange_whenFromAfterTo() {
        Instant from = Instant.parse("2026-12-31T00:00:00Z");
        Instant to   = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() ->
                reportingService.adminAggregateReport(from, to, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("dateFrom");
    }

    @Test
    void customerReport_allowsSameFromAndTo() {
        Customer customer = customer(1L, "Acme");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        Instant same = Instant.parse("2026-06-15T12:00:00Z");
        when(ticketRepository.countByStatusForCustomer(eq(1L), eq(same), eq(same)))
                .thenReturn(new ArrayList<>());
        when(ticketTagRepository.countTagsByCustomerAndDateRange(eq(1L), eq(same), eq(same)))
                .thenReturn(new ArrayList<>());

        CustomerTicketReportResponse report = reportingService.customerReport(1L, same, same);

        assertThat(report.totalCount()).isZero();
    }

    @Test
    void statusBucketSet_openStatusesAreNonFinal() {
        assertThat(ReportingService.OPEN_STATUSES).containsExactlyInAnyOrder(
                TicketStatus.NEW, TicketStatus.TRIAGED, TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER, TicketStatus.REOPENED
        );
        assertThat(ReportingService.OPEN_STATUSES).doesNotContain(
                TicketStatus.RESOLVED, TicketStatus.CLOSED
        );
    }

    // ── agingBuckets ──────────────────────────────────────────────────────────

    @Test
    void agingBuckets_bucketsTicketsByCreatedAtAge() {
        Ticket under4h  = ticketAgedHours(2);
        Ticket h4to24   = ticketAgedHours(10);
        Ticket d1to3    = ticketAgedHours(48);
        Ticket d3to7    = ticketAgedHours(120);
        Ticket over7d   = ticketAgedHours(200);

        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(under4h, h4to24, d1to3, d3to7, over7d));

        AgingBucketsResponse result = reportingService.agingBuckets(null);

        assertThat(result.under4h()).isEqualTo(1);
        assertThat(result.h4to24()).isEqualTo(1);
        assertThat(result.d1to3()).isEqualTo(1);
        assertThat(result.d3to7()).isEqualTo(1);
        assertThat(result.over7d()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(5);
    }

    @Test
    void agingBuckets_returnsAllZeros_whenNoOpenTickets() {
        when(ticketRepository.findAll(any(Specification.class))).thenReturn(List.of());

        AgingBucketsResponse result = reportingService.agingBuckets(null);

        assertThat(result.total()).isZero();
        assertThat(result.under4h()).isZero();
        assertThat(result.over7d()).isZero();
    }

    // ── workloadSummary ───────────────────────────────────────────────────────

    @Test
    void workloadSummary_returnsAssigneeAndGroupWorkload() {
        // [assignedUserId, status, count]
        List<Object[]> assigneeRows = statusCounts(
                new Object[]{10L, TicketStatus.IN_PROGRESS, 3L},
                new Object[]{10L, TicketStatus.WAITING_CUSTOMER, 1L}
        );
        // [groupId, isUnassigned, status, count]
        List<Object[]> groupRows = statusCounts(
                new Object[]{20L, 0, TicketStatus.IN_PROGRESS, 5L}
        );

        when(ticketRepository.countActiveByAssignedUser()).thenReturn(assigneeRows);
        when(ticketRepository.countActiveByGroup()).thenReturn(groupRows);
        when(userRepository.findAllById(any())).thenReturn(List.of(user(10L, "alice")));
        when(groupRepository.findAllById(any())).thenReturn(List.of(group(20L, "Support")));

        WorkloadSummaryResponse result = reportingService.workloadSummary();

        assertThat(result.byAssignee()).hasSize(1);
        WorkloadSummaryResponse.AssigneeWorkload aw = result.byAssignee().get(0);
        assertThat(aw.userId()).isEqualTo(10L);
        assertThat(aw.username()).isEqualTo("alice");
        assertThat(aw.activeCount()).isEqualTo(4);  // IN_PROGRESS(3) + WAITING_CUSTOMER(1)
        assertThat(aw.waitingCustomerCount()).isEqualTo(1);

        assertThat(result.byGroup()).hasSize(1);
        assertThat(result.byGroup().get(0).groupName()).isEqualTo("Support");
        assertThat(result.byGroup().get(0).activeCount()).isEqualTo(5);
    }

    // ── dailyTrend ────────────────────────────────────────────────────────────

    @Test
    void dailyTrend_mapsDbRowsToDataPoints() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-03T23:59:59Z");

        List<Object[]> rows = List.of(
                new Object[]{"2026-01-01", 5L, 2L, 1L},
                new Object[]{"2026-01-02", 3L, 1L, 0L}
        );
        when(ticketRepository.dailyVolumeTrend(null, from, to)).thenReturn(rows);

        List<TrendDataPoint> result = reportingService.dailyTrend(null, from, to);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).date()).isEqualTo("2026-01-01");
        assertThat(result.get(0).created()).isEqualTo(5L);
        assertThat(result.get(0).resolved()).isEqualTo(2L);
        assertThat(result.get(0).closed()).isEqualTo(1L);
    }

    @Test
    void dailyTrend_throwsInvalidDateRange_whenFromAfterTo() {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to   = Instant.parse("2026-01-01T00:00:00Z");

        assertThatThrownBy(() -> reportingService.dailyTrend(null, from, to))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    // ── executiveSummary ──────────────────────────────────────────────────────

    @Test
    void executiveSummary_returnsCorrectCountsAndRates() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-12-31T23:59:59Z");

        when(ticketRepository.countByStatusForCustomer(eq(1L), any(), any()))
                .thenReturn(statusCounts(
                        row(TicketStatus.NEW, 5L),
                        row(TicketStatus.IN_PROGRESS, 3L),
                        row(TicketStatus.RESOLVED, 4L),
                        row(TicketStatus.CLOSED, 2L)
                ));
        when(ticketRepository.countBreachedResolutionSla(1L)).thenReturn(1L);
        when(ticketRepository.avgFirstResponseMinutes(eq(1L), any(), any())).thenReturn(45.0);
        when(ticketRepository.avgResolutionMinutes(eq(1L), any(), any())).thenReturn(300.0);
        when(transferRepository.countDistinctTransferredTickets(eq(1L), any(), any())).thenReturn(2L);

        var result = reportingService.executiveSummary(1L, from, to);

        assertThat(result.totalTickets()).isEqualTo(14);
        assertThat(result.activeTickets()).isEqualTo(8); // NEW(5) + IN_PROGRESS(3)
        assertThat(result.resolvedTickets()).isEqualTo(4);
        assertThat(result.closedTickets()).isEqualTo(2);
        assertThat(result.breachedTickets()).isEqualTo(1);
        assertThat(result.avgFirstResponseMinutes()).isEqualTo(45L);
        assertThat(result.avgResolutionMinutes()).isEqualTo(300L);
    }

    @Test
    void executiveSummary_usesGlobalCounts_whenCustomerIdNull() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-12-31T23:59:59Z");

        when(ticketRepository.countByStatusGlobal(any(), any()))
                .thenReturn(statusCounts(row(TicketStatus.NEW, 10L)));
        when(ticketRepository.countBreachedResolutionSla(null)).thenReturn(0L);
        when(ticketRepository.avgFirstResponseMinutes(eq(null), any(), any())).thenReturn(null);
        when(ticketRepository.avgResolutionMinutes(eq(null), any(), any())).thenReturn(null);
        when(transferRepository.countDistinctTransferredTickets(eq(null), any(), any())).thenReturn(0L);

        var result = reportingService.executiveSummary(null, from, to);

        assertThat(result.totalTickets()).isEqualTo(10);
        assertThat(result.avgFirstResponseMinutes()).isNull();
        assertThat(result.breachedTickets()).isZero();
    }

    // ── customerHealthSummaries ───────────────────────────────────────────────

    @Test
    void customerHealthSummaries_returnsStable_whenNoBreaches() {
        Customer c = customer(1L, "Acme");
        when(customerRepository.findAll()).thenReturn(List.of(c));
        when(ticketRepository.countByStatusForCustomers(eq(List.of(1L)), eq(null), eq(null)))
                .thenReturn(statusCounts(row3(1L, TicketStatus.IN_PROGRESS, 3L)));
        when(ticketRepository.countBreachedResolutionSla(1L)).thenReturn(0L);
        when(ticketRepository.avgFirstResponseMinutes(eq(1L), eq(null), eq(null))).thenReturn(30.0);
        when(ticketRepository.avgResolutionMinutes(eq(1L), eq(null), eq(null))).thenReturn(180.0);

        List<CustomerHealthSummary> result = reportingService.customerHealthSummaries();

        assertThat(result).hasSize(1);
        CustomerHealthSummary summary = result.get(0);
        assertThat(summary.customerId()).isEqualTo(1L);
        assertThat(summary.openTickets()).isEqualTo(3);
        assertThat(summary.healthScore()).isEqualTo(CustomerHealthSummary.HealthScore.STABLE);
    }

    @Test
    void customerHealthSummaries_returnsAtRisk_whenBreachedSlaPresent() {
        Customer c = customer(1L, "Acme");
        when(customerRepository.findAll()).thenReturn(List.of(c));
        when(ticketRepository.countByStatusForCustomers(any(), any(), any()))
                .thenReturn(statusCounts(row3(1L, TicketStatus.IN_PROGRESS, 5L)));
        when(ticketRepository.countBreachedResolutionSla(1L)).thenReturn(2L);
        when(ticketRepository.avgFirstResponseMinutes(any(), any(), any())).thenReturn(null);
        when(ticketRepository.avgResolutionMinutes(any(), any(), any())).thenReturn(null);

        List<CustomerHealthSummary> result = reportingService.customerHealthSummaries();

        assertThat(result.get(0).healthScore()).isEqualTo(CustomerHealthSummary.HealthScore.AT_RISK);
        assertThat(result.get(0).breachedSlaCount()).isEqualTo(2);
    }

    @Test
    void customerHealthSummaries_returnsEmptyList_whenNoCustomers() {
        when(customerRepository.findAll()).thenReturn(List.of());

        List<CustomerHealthSummary> result = reportingService.customerHealthSummaries();

        assertThat(result).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Builds a typed List<Object[]> from individual rows — avoids List.of(Object...) type erasure. */
    @SafeVarargs
    private static List<Object[]> statusCounts(Object[]... rows) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] row : rows) {
            list.add(row);
        }
        return list;
    }

    /** (status, count) row for customer-level queries. */
    private static Object[] row(TicketStatus status, long count) {
        return new Object[]{status, count};
    }

    /** (customerId, status, count) row for admin aggregate queries. */
    private static Object[] row3(long customerId, TicketStatus status, long count) {
        return new Object[]{customerId, status, count};
    }

    private static void setTagId(Tag tag, Long id) {
        try {
            var f = Tag.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(tag, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Customer customer(Long id, String name) {
        Customer c = new Customer();
        c.setName(name);
        c.setIsActive(true);
        try {
            var f = Customer.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
            var ca = Customer.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(c, Instant.now());
            var ua = Customer.class.getDeclaredField("updatedAt");
            ua.setAccessible(true);
            ua.set(c, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }

    private Ticket ticketAgedHours(long hoursAgo) {
        Ticket t = new Ticket();
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setPriority(TicketPriority.MEDIUM);
        try {
            var f = Ticket.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(t, Instant.now().minus(hoursAgo, ChronoUnit.HOURS));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private User user(Long id, String username) {
        User u = new User();
        u.setUsername(username);
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private Group group(Long id, String name) {
        Group g = new Group();
        g.setName(name);
        try {
            var f = Group.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(g, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return g;
    }
}
