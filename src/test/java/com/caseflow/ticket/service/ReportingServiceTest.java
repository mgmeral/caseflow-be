package com.caseflow.ticket.service;

import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.api.dto.CustomerTicketReportResponse;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock CustomerRepository customerRepository;
    @Mock TagRepository tagRepository;
    @Mock TicketTagRepository ticketTagRepository;

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
}
