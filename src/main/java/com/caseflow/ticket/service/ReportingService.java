package com.caseflow.ticket.service;

import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.common.exception.InvalidDateRangeException;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.api.dto.CustomerTicketReportResponse;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reporting service for ticket statistics.
 *
 * <h2>Status bucket mapping (backend-authoritative)</h2>
 * FE must never derive buckets from raw status values — always use the response fields.
 * <ul>
 *   <li>{@code newCount}             = NEW</li>
 *   <li>{@code inProgressCount}      = TRIAGED + ASSIGNED + IN_PROGRESS</li>
 *   <li>{@code waitingCustomerCount} = WAITING_CUSTOMER</li>
 *   <li>{@code resolvedCount}        = RESOLVED</li>
 *   <li>{@code closedCount}          = CLOSED</li>
 *   <li>{@code reopenedCount}        = REOPENED</li>
 *   <li>{@code openCount}            = all non-final (NEW + TRIAGED + ASSIGNED + IN_PROGRESS
 *                                      + WAITING_CUSTOMER + REOPENED)</li>
 *   <li>{@code totalCount}           = all tickets created in the date window</li>
 * </ul>
 *
 * <h2>Date range interpretation</h2>
 * Tickets are selected by {@code created_at} within [from, to].
 * Status values reflect current state at report generation time (not historical state at creation).
 *
 * <h2>Query strategy</h2>
 * Counts are computed at the database level — no full ticket rows are loaded into memory.
 * Admin aggregate uses a single bulk query per page (not one query per customer).
 */
@Service
public class ReportingService {

    private static final Logger log = LoggerFactory.getLogger(ReportingService.class);

    // ── Backend-owned status bucket sets ──────────────────────────────────────

    private static final Set<TicketStatus> NEW_STATUSES =
            EnumSet.of(TicketStatus.NEW);

    private static final Set<TicketStatus> IN_PROGRESS_STATUSES =
            EnumSet.of(TicketStatus.TRIAGED, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);

    private static final Set<TicketStatus> WAITING_CUSTOMER_STATUSES =
            EnumSet.of(TicketStatus.WAITING_CUSTOMER);

    private static final Set<TicketStatus> RESOLVED_STATUSES =
            EnumSet.of(TicketStatus.RESOLVED);

    private static final Set<TicketStatus> CLOSED_STATUSES =
            EnumSet.of(TicketStatus.CLOSED);

    private static final Set<TicketStatus> REOPENED_STATUSES =
            EnumSet.of(TicketStatus.REOPENED);

    /** All non-terminal statuses — tickets still requiring attention. */
    static final Set<TicketStatus> OPEN_STATUSES = EnumSet.of(
            TicketStatus.NEW, TicketStatus.TRIAGED, TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER, TicketStatus.REOPENED);

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final TagRepository tagRepository;
    private final TicketTagRepository ticketTagRepository;

    public ReportingService(TicketRepository ticketRepository,
                            CustomerRepository customerRepository,
                            TagRepository tagRepository,
                            TicketTagRepository ticketTagRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.tagRepository = tagRepository;
        this.ticketTagRepository = ticketTagRepository;
    }

    // ── Customer report ───────────────────────────────────────────────────────

    /**
     * Generates a ticket report for a single customer within the given date range.
     *
     * @param customerId target customer
     * @param from       start of window (inclusive), null = unbounded
     * @param to         end of window (inclusive), null = unbounded
     */
    @Transactional(readOnly = true)
    public CustomerTicketReportResponse customerReport(Long customerId, Instant from, Instant to) {
        validateDateRange(from, to);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        List<Object[]> rawCounts = ticketRepository.countByStatusForCustomer(customerId, from, to);
        StatusCounts counts = computeCountsFromQuery(rawCounts);

        List<CustomerTicketReportResponse.TagCount> tagBreakdown =
                computeTagBreakdownForCustomer(customerId, from, to);

        log.info("REPORT customer: {}, from: {}, to: {}, total: {}",
                customerId, from, to, counts.total());

        return new CustomerTicketReportResponse(
                customer.getId(),
                customer.getName(),
                from,
                to,
                counts.total(),
                counts.open(),
                counts.newCount(),
                counts.inProgress(),
                counts.waitingCustomer(),
                counts.resolved(),
                counts.closed(),
                counts.reopened(),
                tagBreakdown
        );
    }

    // ── Admin aggregate report ────────────────────────────────────────────────

    /**
     * Returns a paginated cross-customer aggregate report.
     *
     * <p>Uses a single bulk ticket-count query per page of customers rather than
     * per-customer queries. This avoids N+1 behavior when page size is large.
     *
     * @param from     start of window (inclusive), null = unbounded
     * @param to       end of window (inclusive), null = unbounded
     * @param pageable pagination and sorting
     */
    @Transactional(readOnly = true)
    public Page<AdminCustomerReportRow> adminAggregateReport(Instant from, Instant to,
                                                              Pageable pageable) {
        validateDateRange(from, to);
        Page<Customer> customerPage = customerRepository.findAll(pageable);
        List<Customer> customers = customerPage.getContent();

        if (customers.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, customerPage.getTotalElements());
        }

        // One bulk query for all customers on this page — eliminates N+1
        List<Long> customerIds = customers.stream().map(Customer::getId).toList();
        List<Object[]> rawCounts = ticketRepository.countByStatusForCustomers(customerIds, from, to);

        // Pivot: customerId -> Map<TicketStatus, Long>
        Map<Long, Map<TicketStatus, Long>> byCustomer = new HashMap<>();
        for (Object[] row : rawCounts) {
            Long customerId = (Long) row[0];
            TicketStatus status = (TicketStatus) row[1];
            long count = ((Number) row[2]).longValue();
            byCustomer.computeIfAbsent(customerId, k -> new EnumMap<>(TicketStatus.class))
                    .put(status, count);
        }

        List<AdminCustomerReportRow> rows = customers.stream()
                .map(customer -> {
                    Map<TicketStatus, Long> statusMap =
                            byCustomer.getOrDefault(customer.getId(), Map.of());
                    StatusCounts counts = computeCountsFromMap(statusMap);
                    return new AdminCustomerReportRow(
                            customer.getId(),
                            customer.getName(),
                            customer.getColorHex(),
                            counts.total(),
                            counts.open(),
                            counts.newCount(),
                            counts.inProgress(),
                            counts.waitingCustomer(),
                            counts.resolved(),
                            counts.closed(),
                            counts.reopened()
                    );
                }).toList();

        log.info("REPORT admin aggregate — from: {}, to: {}, customerCount: {}",
                from, to, rows.size());
        return new PageImpl<>(rows, pageable, customerPage.getTotalElements());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Converts raw (status, count) pairs from a JPQL aggregate query into bucket counts.
     */
    private StatusCounts computeCountsFromQuery(List<Object[]> rawCounts) {
        Map<TicketStatus, Long> statusMap = new EnumMap<>(TicketStatus.class);
        for (Object[] row : rawCounts) {
            TicketStatus status = (TicketStatus) row[0];
            long count = ((Number) row[1]).longValue();
            statusMap.put(status, count);
        }
        return computeCountsFromMap(statusMap);
    }

    private StatusCounts computeCountsFromMap(Map<TicketStatus, Long> statusMap) {
        long total           = statusMap.values().stream().mapToLong(Long::longValue).sum();
        long newCount        = sumStatuses(statusMap, NEW_STATUSES);
        long inProgress      = sumStatuses(statusMap, IN_PROGRESS_STATUSES);
        long waitingCustomer = sumStatuses(statusMap, WAITING_CUSTOMER_STATUSES);
        long resolved        = sumStatuses(statusMap, RESOLVED_STATUSES);
        long closed          = sumStatuses(statusMap, CLOSED_STATUSES);
        long reopened        = sumStatuses(statusMap, REOPENED_STATUSES);
        long open            = sumStatuses(statusMap, OPEN_STATUSES);
        return new StatusCounts(total, open, newCount, inProgress, waitingCustomer,
                resolved, closed, reopened);
    }

    private long sumStatuses(Map<TicketStatus, Long> statusMap, Set<TicketStatus> statuses) {
        return statuses.stream().mapToLong(s -> statusMap.getOrDefault(s, 0L)).sum();
    }

    private List<CustomerTicketReportResponse.TagCount> computeTagBreakdownForCustomer(
            Long customerId, Instant from, Instant to) {
        List<Object[]> rawCounts = ticketTagRepository.countTagsByCustomerAndDateRange(
                customerId, from, to);
        if (rawCounts.isEmpty()) return List.of();

        // Native query returns tag_id as a Number (BigInteger on PostgreSQL)
        List<Long> tagIds = rawCounts.stream()
                .map(r -> ((Number) r[0]).longValue())
                .toList();
        Map<Long, Tag> tagMap = tagRepository.findAllById(tagIds)
                .stream().collect(Collectors.toMap(Tag::getId, t -> t));

        List<CustomerTicketReportResponse.TagCount> result = new ArrayList<>();
        for (Object[] row : rawCounts) {
            Long tagId = ((Number) row[0]).longValue();
            long tagCount = ((Number) row[1]).longValue();
            Tag tag = tagMap.get(tagId);
            if (tag != null) {
                result.add(new CustomerTicketReportResponse.TagCount(
                        tag.getId(), tag.getCode(), tag.getName(), tag.getColor(), tagCount));
            }
        }
        return result;
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("dateFrom must not be after dateTo");
        }
    }

    /** Immutable holder for computed status bucket values. */
    private record StatusCounts(long total, long open, long newCount, long inProgress,
                                long waitingCustomer, long resolved, long closed, long reopened) {}
}
