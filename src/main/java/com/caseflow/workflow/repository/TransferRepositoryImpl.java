package com.caseflow.workflow.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;

public class TransferRepositoryImpl implements TransferRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public long countDistinctTransferredTickets(Long customerId, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(DISTINCT tr.ticketId) FROM Transfer tr, Ticket t WHERE t.id = tr.ticketId ");
        if (customerId != null) jpql.append("AND t.customerId = :customerId ");
        if (from != null) jpql.append("AND t.createdAt >= :from ");
        if (to != null) jpql.append("AND t.createdAt <= :to ");

        var query = em.createQuery(jpql.toString(), Long.class);
        if (customerId != null) query.setParameter("customerId", customerId);
        if (from != null) query.setParameter("from", from);
        if (to != null) query.setParameter("to", to);
        return query.getSingleResult();
    }
}
