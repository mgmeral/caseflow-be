package com.caseflow.ticket.service;

import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.ticket.api.dto.DashboardStatsResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketSlaFilter;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketSpecification;
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
 *   <li><b>myActionRequired</b> — assignedUserId = caller AND status NOT IN (RESOLVED, CLOSED)</li>
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

    /**
     * Returns dashboard stats for the given caller.
     *
     * @param currentUserId the authenticated user id — used for myActionRequired; null = skip that widget
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(Long currentUserId) {
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

        if (currentUserId != null) {
            Specification<Ticket> mySpec = activeSpec.and(TicketSpecification.hasAssignedUserId(currentUserId));
            List<Ticket> myTickets = ticketRepository.findAll(mySpec);
            myActionRequiredCount = (long) myTickets.size();
            myItems = buildMyItems(myTickets);
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
