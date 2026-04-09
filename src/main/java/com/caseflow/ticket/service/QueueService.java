package com.caseflow.ticket.service;

import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.ticket.api.dto.QueueStatsResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketScopeSpecification;
import com.caseflow.ticket.repository.TicketSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Queue service — authoritative triage/assignment workspace dataset.
 *
 * <h2>Queue membership</h2>
 * A ticket is queue-eligible when:
 * <ol>
 *   <li>assignedUserId IS NULL (no user owner yet)</li>
 *   <li>status NOT IN (RESOLVED, CLOSED) — terminal tickets are never in the queue</li>
 * </ol>
 *
 * <p>After a successful assignment the ticket loses its unassigned state and no longer
 * appears in queue results on the next read. Counts and rows share the same predicate.
 */
@Service
public class QueueService {

    private static final Set<TicketStatus> TERMINAL_STATUSES = Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private static final Set<TicketPriority> HIGH_OR_CRITICAL =
            Set.of(TicketPriority.HIGH, TicketPriority.CRITICAL);

    /**
     * Proxy SLA threshold for HIGH/CRITICAL tickets (hours).
     * A HIGH or CRITICAL ticket is considered SLA-breached when it has been in its
     * current status for longer than this threshold with no workflow action taken.
     * This is a conservative proxy — explicit per-priority SLA config can replace it later.
     */
    private static final long SLA_BREACHED_HOURS = 4L;

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;
    private final TicketReadService ticketReadService;

    public QueueService(TicketRepository ticketRepository,
                        CustomerRepository customerRepository,
                        TicketReadService ticketReadService) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
        this.ticketReadService = ticketReadService;
    }

    /**
     * Returns a page of queue-eligible tickets, optionally filtered by priority.
     * Scope spec (if non-null) limits visibility to the caller's group scope.
     *
     * @param priority  optional priority filter applied on top of queue membership
     * @param scopeSpec optional caller visibility scope; null = no restriction
     * @param pageable  paging and sorting
     */
    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> getQueue(TicketPriority priority,
                                                Specification<Ticket> scopeSpec,
                                                Pageable pageable) {
        Specification<Ticket> spec = queueMembership()
                .and(TicketSpecification.hasPriority(priority))
                .and(scopeSpec);
        return ticketReadService.searchScoped(spec, pageable);
    }

    /**
     * Returns queue chip counts.
     * All counts are computed from the same base queue membership predicate.
     * The scopeSpec (if non-null) is applied consistently across all chip counts.
     *
     * @param scopeSpec optional caller visibility scope
     */
    @Transactional(readOnly = true)
    public QueueStatsResponse getStats(Specification<Ticket> scopeSpec) {
        Instant threshold8h  = Instant.now().minus(8, ChronoUnit.HOURS);
        Instant thresholdSla = Instant.now().minus(SLA_BREACHED_HOURS, ChronoUnit.HOURS);

        Specification<Ticket> base        = queueMembership().and(scopeSpec);
        Specification<Ticket> highCritical = base.and(priorityIn(HIGH_OR_CRITICAL));
        Specification<Ticket> waiting8h    = base.and(statusChangedBefore(threshold8h));
        Specification<Ticket> slaBreached  = base.and(priorityIn(HIGH_OR_CRITICAL))
                                                  .and(statusChangedBefore(thresholdSla));

        long all          = ticketRepository.count(base);
        long highCrit     = ticketRepository.count(highCritical);
        long waiting      = ticketRepository.count(waiting8h);
        long breached     = ticketRepository.count(slaBreached);

        return new QueueStatsResponse(all, highCrit, waiting, breached);
    }

    // ── Predicates ────────────────────────────────────────────────────────────

    /**
     * Core queue membership predicate:
     * assignedUserId IS NULL AND status NOT IN (RESOLVED, CLOSED).
     */
    public static Specification<Ticket> queueMembership() {
        return TicketScopeSpecification.unassignedUser()
                .and((root, query, cb) -> cb.not(root.get("status").in(TERMINAL_STATUSES)));
    }

    private static Specification<Ticket> priorityIn(Set<TicketPriority> priorities) {
        return (root, query, cb) -> root.get("priority").in(priorities);
    }

    /**
     * COALESCE(statusChangedAt, createdAt) < threshold.
     * Uses statusChangedAt (set on every status transition) rather than updatedAt,
     * which can be bumped by non-workflow events such as note creation or tag changes.
     * Falls back to createdAt for tickets created before the statusChangedAt migration.
     */
    private static Specification<Ticket> statusChangedBefore(Instant threshold) {
        return (root, query, cb) -> cb.lessThan(
                cb.coalesce(root.<Instant>get("statusChangedAt"), root.get("createdAt")),
                threshold);
    }
}
