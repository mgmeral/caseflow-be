package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria API implementation of {@link TicketRepositoryCustom}.
 *
 * <p>Predicate construction is conditional: date bounds are added to the WHERE clause
 * only when non-null. This avoids the PostgreSQL "could not determine data type of
 * parameter $N" error that occurs when a null literal appears in a type-ambiguous
 * position (e.g. {@code :param IS NULL OR col >= :param}).
 */
public class TicketRepositoryImpl implements TicketRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Object[]> countByStatusForCustomer(Long customerId, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(t.get("customerId"), customerId));
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(t.<Instant>get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(t.<Instant>get("createdAt"), to));
        }

        cq.multiselect(t.get("status"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("status"));

        return em.createQuery(cq).getResultList();
    }

    @Override
    public List<Object[]> countByStatusForCustomers(List<Long> customerIds, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(t.get("customerId").in(customerIds));
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(t.<Instant>get("createdAt"), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(t.<Instant>get("createdAt"), to));
        }

        cq.multiselect(t.get("customerId"), t.get("status"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("customerId"), t.get("status"));

        return em.createQuery(cq).getResultList();
    }
}
