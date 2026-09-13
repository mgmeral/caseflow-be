package com.caseflow.ticket.service;

import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.ticket.api.dto.DashboardStatsResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketSlaFilter;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketScopeSpecification;
import com.caseflow.ticket.repository.TicketSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
 * Authoritative source of dashboard aggregate statistics.
 *
 * <h2>Business rules</h2>
 * All counts are computed from the same tickets table with explicit predicates.
 * Any dashboard widget list that corresponds to a count MUST use the same predicate.
 *
 * <ul>
 *   <li><b>totalTickets</b> — all rows in the tickets table</li>
 *   <li><b>activeTickets</b> — status NOT IN (RESOLVED, CLOSED)</li>
 *   <li><b>resolvedTickets</b> — status = RESOLVED</li>
 *   <li><b>closedTickets</b> — status = CLOSED</li>
 *   <li><b>unassignedTickets</b> — assignedUserId IS NULL AND status NOT IN (RESOLVED, CLOSED)</li>
 *   <li><b>waitingOver24h</b> — COALESCE(statusChangedAt, createdAt) &lt; now-24h AND status NOT IN (RESOLVED, CLOSED)</li>
 *   <li><b>breachedSlaCount</b> — resolutionDueAt &lt; now AND status NOT IN (RESOLVED, CLOSED)</li>
 *   <li><b>atRiskSlaCount</b> — resolutionDueAt IS NOT NULL AND resolutionDueAt BETWEEN now AND now+4h AND status NOT terminal.
 *       Fixed 4-hour warning window; per-policy thresholds are available in the SLA detail view.</li>
 *   <li><b>myActionRequired</b> — depends on the caller's {@code ticketScope}:
 *       <ul>
 *         <li>{@code ASSIGNED_ONLY} / {@code OWN_AND_OWN_GROUPS} (roles that personally own
 *             tickets, e.g. Viewer/Agent): assignedUserId = caller AND status NOT IN (RESOLVED, CLOSED) —
 *             unchanged "my personal queue" semantics.</li>
 *         <li>{@code OWN_GROUPS} / {@code ALL} (roles that orchestrate rather than personally
 *             own tickets, e.g. Supervisor/Admin): open tickets, visible to the caller per
 *             {@link TicketScopeSpecification#visibleTo}, that are SLA-breached, SLA-at-risk, or
 *             unassigned — sorted most-urgent-first, capped to the top 5. "Assigned to me" would
 *             almost always be empty for these roles and tells them nothing actionable.</li>
 *       </ul>
 *   </li>
 * </ul>
 */
@Service
public class DashboardService {

    private static final Set<TicketStatus> TERMINAL_STATUSES = Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final TicketRepository ticketRepository;
    private final CustomerRepository customerRepository;

    public DashboardService(TicketRepository ticketRepository, CustomerRepository customerRepository) {
        this.ticketRepository = ticketRepository;
        this.customerRepository = customerRepository;
    }

    /** Number of items returned in the operationally-urgent {@code myActionRequired} queue (OWN_GROUPS/ALL scopes). */
    private static final int URGENT_QUEUE_SIZE = 5;

    /**
     * Returns dashboard stats for the given caller.
     *
     * @param currentUserId the authenticated user id — used for myActionRequired; null = skip that widget
     * @param scope         the caller's ticketScope — determines myActionRequired's business rule (see class javadoc); null = skip that widget
     * @param groupIds      the caller's group memberships — only consulted for OWN_GROUPS/ALL scope
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(Long currentUserId, TicketScope scope, List<Long> groupIds) {
        Instant now = Instant.now();
        Instant threshold24h = now.minus(24, ChronoUnit.HOURS);
        // AT_RISK_WINDOW is the shared constant — same value used by TicketSlaFilter.AT_RISK for list drill-down
        Instant atRiskThreshold = now.plus(TicketSlaFilter.AT_RISK_WINDOW);

        Specification<Ticket> activeSpec = notTerminal();
        Specification<Ticket> resolvedSpec = TicketSpecification.hasStatus(TicketStatus.RESOLVED);
        Specification<Ticket> closedSpec   = TicketSpecification.hasStatus(TicketStatus.CLOSED);
        Specification<Ticket> unassignedSpec = unassignedAndActive();
        Specification<Ticket> waitingSpec  = activeSpec.and(statusChangedBefore(threshold24h));

        long total      = ticketRepository.count();
        long active     = ticketRepository.count(activeSpec);
        long resolved   = ticketRepository.count(resolvedSpec);
        long closed     = ticketRepository.count(closedSpec);
        long unassigned = ticketRepository.count(unassignedSpec);
        long waiting    = ticketRepository.count(waitingSpec);
        long breached   = ticketRepository.countBreachedResolutionSla(null);
        long atRisk     = ticketRepository.countAtRiskResolutionSla(null, atRiskThreshold);

        Long myActionRequiredCount = null;
        List<DashboardStatsResponse.MyActionRequiredItem> myItems = List.of();

        if (currentUserId != null && scope != null) {
            if (scope == TicketScope.ASSIGNED_ONLY || scope == TicketScope.OWN_AND_OWN_GROUPS) {
                Specification<Ticket> mySpec = activeSpec.and(TicketSpecification.hasAssignedUserId(currentUserId));
                List<Ticket> myTickets = ticketRepository.findAll(mySpec);
                myActionRequiredCount = (long) myTickets.size();
                myItems = buildMyItems(myTickets);
            } else {
                Specification<Ticket> urgentSpec = Specification.where(activeSpec)
                        .and(TicketScopeSpecification.visibleTo(currentUserId, groupIds, scope))
                        .and(Specification.<Ticket>where(TicketSpecification.hasSlaBreached(now))
                                .or(TicketSpecification.hasSlaAtRisk(now, atRiskThreshold))
                                .or(TicketScopeSpecification.unassignedUser()));
                Sort urgencySort = Sort.by(Sort.Order.asc("resolutionDueAt").nullsLast());
                Page<Ticket> urgentPage = ticketRepository.findAll(urgentSpec, PageRequest.of(0, URGENT_QUEUE_SIZE, urgencySort));
                myActionRequiredCount = urgentPage.getTotalElements();
                myItems = buildMyItems(urgentPage.getContent());
            }
        }

        return new DashboardStatsResponse(
                total, active, resolved, closed, unassigned, waiting, breached, atRisk,
                myActionRequiredCount, myItems);
    }

    // ── Predicates ────────────────────────────────────────────────────────────

    /** Status NOT IN (RESOLVED, CLOSED). */
    public static Specification<Ticket> notTerminal() {
        return (root, query, cb) -> cb.not(root.get("status").in(TERMINAL_STATUSES));
    }

    /** assignedUserId IS NULL AND status NOT IN terminal. */
    public static Specification<Ticket> unassignedAndActive() {
        return notTerminal().and(
                (root, query, cb) -> cb.isNull(root.get("assignedUserId"))
        );
    }

    /**
     * statusChangedAt (or createdAt when null) < threshold.
     * Uses statusChangedAt to avoid false-positives from non-workflow updates
     * such as note creation or tag changes bumping updatedAt.
     */
    private static Specification<Ticket> statusChangedBefore(Instant threshold) {
        return (root, query, cb) -> cb.lessThan(
                cb.coalesce(root.<Instant>get("statusChangedAt"), root.get("createdAt")),
                threshold);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<DashboardStatsResponse.MyActionRequiredItem> buildMyItems(List<Ticket> tickets) {
        if (tickets.isEmpty()) return List.of();

        Set<Long> customerIds = tickets.stream()
                .filter(t -> t.getCustomerId() != null)
                .map(Ticket::getCustomerId)
                .collect(Collectors.toSet());
        Map<Long, String> customerNames = customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

        return tickets.stream().map(t -> new DashboardStatsResponse.MyActionRequiredItem(
                t.getId(),
                t.getTicketNo(),
                t.getSubject(),
                t.getStatus().name(),
                t.getPriority().name(),
                t.getCustomerId() != null ? customerNames.get(t.getCustomerId()) : null
        )).toList();
    }
}
