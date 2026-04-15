package com.caseflow.ticket.service;

import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.common.exception.InvalidDateRangeException;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.UserRepository;
import com.caseflow.ticket.api.dto.AdminCustomerReportRow;
import com.caseflow.ticket.api.dto.AdminReportSummaryResponse;
import com.caseflow.ticket.api.dto.AgingBucketsResponse;
import com.caseflow.ticket.api.dto.CustomerHealthSummary;
import com.caseflow.ticket.api.dto.CustomerTicketReportResponse;
import com.caseflow.ticket.api.dto.TrendDataPoint;
import com.caseflow.ticket.api.dto.WorkloadSummaryResponse;
import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TagRepository;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketTagRepository;
import com.caseflow.workflow.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final TransferRepository transferRepository;

    public ReportingService(TicketRepository ticketRepository,
                            CustomerRepository customerRepository,
                            TagRepository tagRepository,
                            TicketTagRepository ticketTagRepository,
                            UserRepository userRepository,
                            GroupRepository groupRepository,
                            TransferRepository transferRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.tagRepository = tagRepository;
        this.ticketTagRepository = ticketTagRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.transferRepository = transferRepository;
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

    // ── Aging buckets ─────────────────────────────────────────────────────────

    /**
     * Computes the backlog aging distribution for open (non-terminal) tickets.
     * Age is measured from ticket {@code createdAt} to now.
     *
     * @param customerId optional customer filter; null = global
     */
    @Transactional(readOnly = true)
    public AgingBucketsResponse agingBuckets(Long customerId) {
        Instant now = Instant.now();
        List<Ticket> openTickets = loadOpenTickets(customerId);

        long under4h = 0, h4to24 = 0, d1to3 = 0, d3to7 = 0, over7d = 0;

        for (Ticket t : openTickets) {
            long hours = ChronoUnit.HOURS.between(t.getCreatedAt(), now);
            if (hours < 4) under4h++;
            else if (hours < 24) h4to24++;
            else if (hours < 72) d1to3++;    // 3 days = 72h
            else if (hours < 168) d3to7++;   // 7 days = 168h
            else over7d++;
        }

        return new AgingBucketsResponse(under4h, h4to24, d1to3, d3to7, over7d,
                under4h + h4to24 + d1to3 + d3to7 + over7d);
    }

    // ── Workload aggregates ───────────────────────────────────────────────────

    /**
     * Returns per-assignee and per-group workload aggregates for active tickets.
     */
    @Transactional(readOnly = true)
    public WorkloadSummaryResponse workloadSummary() {
        Instant now = Instant.now();

        // Per-assignee: [assignedUserId, status, count]
        List<Object[]> assigneeRows = ticketRepository.countActiveByAssignedUser();
        // Per-group: [groupId, isUnassigned (int), status, count]
        List<Object[]> groupRows = ticketRepository.countActiveByGroup();

        // Build assignee workload — [active, waitingCustomer, breached]
        Map<Long, long[]> assigneeMap = new LinkedHashMap<>();
        for (Object[] row : assigneeRows) {
            Long userId = (Long) row[0];
            TicketStatus status = (TicketStatus) row[1];
            long count = ((Number) row[2]).longValue();
            long[] buckets = assigneeMap.computeIfAbsent(userId, k -> new long[3]);
            buckets[0] += count; // active
            if (status == TicketStatus.WAITING_CUSTOMER) buckets[1] += count;
        }

        // Real breached-SLA counts per assignee from DB
        Map<Long, Long> breachedByAssignee = ticketRepository.countBreachedByAssignedUser()
                .stream().collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()));

        Map<Long, String> userNames = userRepository.findAllById(assigneeMap.keySet())
                .stream().collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));

        List<WorkloadSummaryResponse.AssigneeWorkload> byAssignee = assigneeMap.entrySet().stream()
                .map(e -> new WorkloadSummaryResponse.AssigneeWorkload(
                        e.getKey(),
                        userNames.getOrDefault(e.getKey(), "Unknown"),
                        e.getValue()[0],
                        e.getValue()[1],
                        breachedByAssignee.getOrDefault(e.getKey(), 0L)
                ))
                .toList();

        // Build group workload — [active, unassigned, breached]
        Map<Long, long[]> groupActiveMap = new LinkedHashMap<>();
        for (Object[] row : groupRows) {
            Long groupId = (Long) row[0];
            int isUnassigned = row[1] instanceof Number ? ((Number) row[1]).intValue() : 0;
            long count = ((Number) row[3]).longValue();
            long[] buckets = groupActiveMap.computeIfAbsent(groupId, k -> new long[2]);
            buckets[0] += count;
            if (isUnassigned == 1) buckets[1] += count;
        }

        // Real breached-SLA counts per group from DB
        Map<Long, Long> breachedByGroup = ticketRepository.countBreachedByGroup()
                .stream().collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).longValue()));

        Map<Long, String> groupNames = groupRepository.findAllById(groupActiveMap.keySet())
                .stream().collect(Collectors.toMap(g -> g.getId(), g -> g.getName()));

        List<WorkloadSummaryResponse.GroupWorkload> byGroup = groupActiveMap.entrySet().stream()
                .map(e -> new WorkloadSummaryResponse.GroupWorkload(
                        e.getKey(),
                        groupNames.getOrDefault(e.getKey(), "Unknown"),
                        e.getValue()[0],
                        e.getValue()[1],
                        breachedByGroup.getOrDefault(e.getKey(), 0L)
                ))
                .toList();

        return new WorkloadSummaryResponse(byAssignee, byGroup);
    }

    // ── Trend endpoint ────────────────────────────────────────────────────────

    /**
     * Returns daily ticket volume trend data within [from, to].
     * Dates with no activity are included as zero-value points.
     *
     * @param customerId optional customer filter; null = global
     * @param from       start of window (inclusive); null = last 30 days
     * @param to         end of window (inclusive); null = now
     */
    @Transactional(readOnly = true)
    public List<TrendDataPoint> dailyTrend(Long customerId, Instant from, Instant to) {
        validateDateRange(from, to);
        List<Object[]> rows = ticketRepository.dailyVolumeTrend(customerId, from, to);
        return rows.stream().map(row -> new TrendDataPoint(
                row[0] != null ? row[0].toString() : "",
                row[1] != null ? ((Number) row[1]).longValue() : 0L,
                row[2] != null ? ((Number) row[2]).longValue() : 0L,
                row[3] != null ? ((Number) row[3]).longValue() : 0L,
                0L // SLA breach per day: future enhancement
        )).toList();
    }

    // ── Executive summary ─────────────────────────────────────────────────────

    /**
     * Returns an executive-level summary of ticket metrics for the given window.
     *
     * @param customerId optional customer filter; null = global
     * @param from       start of window; null = unbounded
     * @param to         end of window; null = unbounded
     */
    @Transactional(readOnly = true)
    public AdminReportSummaryResponse executiveSummary(Long customerId, Instant from, Instant to) {
        validateDateRange(from, to);

        // Status counts
        StatusCounts counts = customerId != null
                ? computeCountsFromQuery(ticketRepository.countByStatusForCustomer(customerId, from, to))
                : computeGlobalCounts(from, to);

        long breached = ticketRepository.countBreachedResolutionSla(customerId);

        // Timing metrics
        Double avgFirstResponse = ticketRepository.avgFirstResponseMinutes(customerId, from, to);
        Double avgResolution = ticketRepository.avgResolutionMinutes(customerId, from, to);

        // Transfer rate: count distinct tickets with at least one transfer (approximate via transfer table)
        long transferredCount = countTransferredTickets(customerId, from, to);

        Double reopenRate = safeRate(counts.reopened(),
                counts.resolved() + counts.closed() + counts.reopened());
        Double transferRate = safeRate(transferredCount, counts.total());
        Double waitingRatio = safeRate(counts.waitingCustomer(), counts.open());
        Double terminalRatio = safeRate(counts.resolved() + counts.closed(), counts.total());

        log.info("REPORT executive summary — customerId: {}, from: {}, to: {}, total: {}, breached: {}",
                customerId, from, to, counts.total(), breached);

        return new AdminReportSummaryResponse(
                from, to,
                counts.total(),
                counts.open(),
                counts.resolved(),
                counts.closed(),
                counts.reopened(),
                counts.waitingCustomer(),
                breached,
                avgFirstResponse != null ? Math.round(avgFirstResponse) : null,
                avgResolution != null ? Math.round(avgResolution) : null,
                counts.total() > 0 ? reopenRate : null,
                counts.total() > 0 ? transferRate : null,
                counts.open() > 0 ? waitingRatio : null,
                counts.total() > 0 ? terminalRatio : null
        );
    }

    // ── Customer health ───────────────────────────────────────────────────────

    /**
     * Returns a health summary for every active customer.
     *
     * <p>Health is scored per the rules in {@link CustomerHealthSummary.HealthScore}.
     */
    @Transactional(readOnly = true)
    public List<CustomerHealthSummary> customerHealthSummaries() {
        List<Customer> customers = customerRepository.findAll();
        if (customers.isEmpty()) return List.of();

        List<Long> customerIds = customers.stream().map(Customer::getId).toList();

        // Bulk status counts for all customers
        List<Object[]> rawCounts = ticketRepository.countByStatusForCustomers(customerIds, null, null);
        Map<Long, Map<TicketStatus, Long>> byCustomer = new HashMap<>();
        for (Object[] row : rawCounts) {
            Long cid = (Long) row[0];
            TicketStatus status = (TicketStatus) row[1];
            long count = ((Number) row[2]).longValue();
            byCustomer.computeIfAbsent(cid, k -> new EnumMap<>(TicketStatus.class)).put(status, count);
        }

        return customers.stream().map(customer -> {
            Long cid = customer.getId();
            Map<TicketStatus, Long> statusMap = byCustomer.getOrDefault(cid, Map.of());
            StatusCounts counts = computeCountsFromMap(statusMap);

            long breached = ticketRepository.countBreachedResolutionSla(cid);
            Double avgFirstResponse = ticketRepository.avgFirstResponseMinutes(cid, null, null);
            Double avgResolution = ticketRepository.avgResolutionMinutes(cid, null, null);

            CustomerHealthSummary.HealthScore health = scoreHealth(
                    counts.open(), breached, avgFirstResponse);

            return new CustomerHealthSummary(
                    cid,
                    customer.getName(),
                    customer.getColorHex(),
                    counts.open(),
                    breached,
                    avgFirstResponse != null ? Math.round(avgFirstResponse) : null,
                    avgResolution != null ? Math.round(avgResolution) : null,
                    health
            );
        }).toList();
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

    /**
     * Loads all open (non-terminal) tickets, optionally filtered by customer.
     * Used for aging bucket computation — loads only essential fields via JPA.
     */
    private List<Ticket> loadOpenTickets(Long customerId) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("status").in(OPEN_STATUSES));
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return ticketRepository.findAll(spec);
    }

    /** Returns global (all-customer) status counts for the given date window. */
    private StatusCounts computeGlobalCounts(Instant from, Instant to) {
        return computeCountsFromQuery(ticketRepository.countByStatusGlobal(from, to));
    }

    /**
     * Counts distinct tickets that have at least one transfer within the given scope.
     * Delegates to {@link com.caseflow.workflow.repository.TransferRepository}.
     */
    private long countTransferredTickets(Long customerId, Instant from, Instant to) {
        try {
            return transferRepository.countDistinctTransferredTickets(customerId, from, to);
        } catch (Exception e) {
            log.warn("REPORT transfer count failed — returning 0", e);
            return 0L;
        }
    }

    /**
     * Divides numerator by denominator, returning null when denominator is zero.
     * Used for rate metrics (reopenRate, transferRate, etc.).
     */
    private static Double safeRate(long numerator, long denominator) {
        if (denominator == 0) return null;
        return (double) numerator / denominator;
    }

    private void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("dateFrom must not be after dateTo");
        }
    }

    /**
     * Computes a customer health score from open ticket count, breached SLA count, and
     * average first-response time.
     *
     * <ul>
     *   <li>AT_RISK  — any breached SLA, or &gt;20 open tickets</li>
     *   <li>WATCH    — avgFirstResponse &gt; 60 min, or &gt;10 open tickets</li>
     *   <li>STABLE   — otherwise</li>
     * </ul>
     */
    private static CustomerHealthSummary.HealthScore scoreHealth(long openCount, long breachedCount,
                                                                   Double avgFirstResponseMinutes) {
        if (breachedCount > 0 || openCount > 20) return CustomerHealthSummary.HealthScore.AT_RISK;
        if (openCount > 10 || (avgFirstResponseMinutes != null && avgFirstResponseMinutes > 60)) {
            return CustomerHealthSummary.HealthScore.WATCH;
        }
        return CustomerHealthSummary.HealthScore.STABLE;
    }

    /** Immutable holder for computed status bucket values. */
    private record StatusCounts(long total, long open, long newCount, long inProgress,
                                long waitingCustomer, long resolved, long closed, long reopened) {}
}
