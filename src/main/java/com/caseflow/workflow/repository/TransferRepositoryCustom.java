package com.caseflow.workflow.repository;

import java.time.Instant;

/**
 * Transfer queries with optional filters. Like {@code TicketRepositoryCustom}, these build
 * their predicates dynamically: a JPQL {@code (:param IS NULL OR col >= :param)} with a null
 * Instant fails on PostgreSQL ("could not determine data type of parameter").
 */
public interface TransferRepositoryCustom {

    /**
     * Counts distinct tickets with at least one transfer, optionally filtered by the ticket's
     * customer and creation window. Every argument may be null (no filter).
     */
    long countDistinctTransferredTickets(Long customerId, Instant from, Instant to);
}
