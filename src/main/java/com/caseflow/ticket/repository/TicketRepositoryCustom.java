package com.caseflow.ticket.repository;

import java.time.Instant;
import java.util.List;

/**
 * Custom aggregate queries that cannot be expressed safely as static JPQL due to nullable
 * date parameters. PostgreSQL cannot determine the data type of a null parameter in the
 * pattern {@code (:param IS NULL OR col >= :param)}, so we build predicates dynamically
 * via the Criteria API instead.
 */
public interface TicketRepositoryCustom {

    /**
     * Returns {@code (TicketStatus, Long count)} pairs for tickets of a customer
     * created within the given date window.
     */
    List<Object[]> countByStatusForCustomer(Long customerId, Instant from, Instant to);

    /**
     * Bulk version — returns {@code (Long customerId, TicketStatus, Long count)} for all given customers.
     */
    List<Object[]> countByStatusForCustomers(List<Long> customerIds, Instant from, Instant to);

    /**
     * Returns daily ticket-volume trend data within [from, to].
     * Each row is {@code (String date "yyyy-MM-dd", Long created, Long resolved, Long closed)}.
     *
     * <p>Dates with no activity are NOT returned — callers must fill gaps.
     *
     * @param customerId optional customer filter; null = global
     * @param from       start of window (inclusive), null = unbounded
     * @param to         end of window (inclusive), null = unbounded
     */
    List<Object[]> dailyVolumeTrend(Long customerId, Instant from, Instant to);

    /**
     * Returns {@code (Long assignedUserId, TicketStatus, Long count)} for all assigned, non-terminal tickets.
     * Used to compute per-assignee workload aggregates.
     */
    List<Object[]> countActiveByAssignedUser();

    /**
     * Returns {@code (Long assignedGroupId, TicketStatus, Long assignedUserId_null_or_not, Long count)}
     * aggregate for non-terminal tickets.
     * Simplified to {@code (Long groupId, boolean isUnassigned, TicketStatus, Long count)}.
     */
    List<Object[]> countActiveByGroup();

    /**
     * Returns average first-response time in minutes for tickets created in the date window
     * that have both {@code createdAt} and {@code firstResponseRespondedAt} set.
     * Returns null if no qualifying tickets exist.
     */
    Double avgFirstResponseMinutes(Long customerId, Instant from, Instant to);

    /**
     * Returns average resolution time in minutes for tickets resolved/closed in the date window.
     * Resolution time = resolvedAt - createdAt (or closedAt - createdAt for CLOSED).
     * Returns null if no qualifying tickets exist.
     */
    Double avgResolutionMinutes(Long customerId, Instant from, Instant to);

    /**
     * Returns the count of tickets with breached resolution SLA (resolutionDueAt exceeded, still open).
     */
    long countBreachedResolutionSla(Long customerId);

    /**
     * Returns the count of non-terminal tickets whose {@code resolutionDueAt} has NOT yet passed
     * but falls before {@code warningThreshold}. These are "at risk" — breach is imminent.
     *
     * <p>Predicate: {@code resolutionDueAt IS NOT NULL AND resolutionDueAt > NOW
     * AND resolutionDueAt <= warningThreshold AND status NOT IN (RESOLVED, CLOSED)}.
     *
     * @param customerId      optional customer filter; null = all customers
     * @param warningThreshold absolute instant defining the end of the at-risk window (e.g. NOW + 4h)
     */
    long countAtRiskResolutionSla(Long customerId, Instant warningThreshold);

    /**
     * Returns {@code (TicketStatus, Long count)} pairs for all tickets created within the given
     * date window, without filtering by customer. Used for global executive summaries.
     */
    List<Object[]> countByStatusGlobal(Instant from, Instant to);

    /**
     * Returns {@code (Long assignedUserId, Long breachedCount)} for all assigned non-terminal
     * tickets where {@code resolutionDueAt} has been exceeded.
     * Used to populate the {@code breached} field in per-assignee workload aggregates.
     */
    List<Object[]> countBreachedByAssignedUser();

    /**
     * Returns {@code (Long assignedGroupId, Long breachedCount)} for non-terminal tickets
     * belonging to a group where {@code resolutionDueAt} has been exceeded.
     * Used to populate the {@code breached} field in per-group workload aggregates.
     */
    List<Object[]> countBreachedByGroup();
}
