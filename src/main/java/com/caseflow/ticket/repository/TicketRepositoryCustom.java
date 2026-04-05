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
     *
     * @param customerId target customer
     * @param from       lower bound (inclusive), {@code null} = unbounded
     * @param to         upper bound (inclusive), {@code null} = unbounded
     */
    List<Object[]> countByStatusForCustomer(Long customerId, Instant from, Instant to);

    /**
     * Bulk version for admin aggregate — returns {@code (Long customerId, TicketStatus, Long count)}
     * for all given customers in a single query.
     *
     * @param customerIds customers to aggregate
     * @param from        lower bound (inclusive), {@code null} = unbounded
     * @param to          upper bound (inclusive), {@code null} = unbounded
     */
    List<Object[]> countByStatusForCustomers(List<Long> customerIds, Instant from, Instant to);
}
