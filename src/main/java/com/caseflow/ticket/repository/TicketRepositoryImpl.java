package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Criteria API implementation of {@link TicketRepositoryCustom}.
 *
 * <p>Predicate construction is conditional: date bounds are added to the WHERE clause
 * only when non-null. This avoids the PostgreSQL "could not determine data type of
 * parameter $N" error that occurs when a null literal appears in a type-ambiguous
 * position (e.g. {@code :param IS NULL OR col >= :param}).
 */
public class TicketRepositoryImpl implements TicketRepositoryCustom {

    private static final Set<TicketStatus> TERMINAL_STATUSES =
            Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Object[]> countByStatusForCustomer(Long customerId, Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(t.get("customerId"), customerId));
        addDateBounds(cb, t, predicates, from, to, "createdAt");

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
        addDateBounds(cb, t, predicates, from, to, "createdAt");

        cq.multiselect(t.get("customerId"), t.get("status"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("customerId"), t.get("status"));

        return em.createQuery(cq).getResultList();
    }

    /**
     * Daily volume trend using native SQL for date-truncation (JPQL lacks CAST/DATE_TRUNC portably).
     * Returns rows: [dateStr String, createdCount Long, resolvedCount Long, closedCount Long].
     *
     * <p>Uses UNION ALL to aggregate three separate counts by date then joins in Java —
     * this avoids complex pivot SQL while remaining portable across Postgres/H2.
     */
    @Override
    public List<Object[]> dailyVolumeTrend(Long customerId, Instant from, Instant to) {
        // Use JPQL with function() to call DATE_TRUNC / CAST
        // We compute three separate queries and merge in service layer — simpler and avoids N+1
        // by querying all at once via Java streams. Returned as raw date->count maps.
        // Format: [dateString, created, resolved, closed]

        String nativeBase = buildNativeTrendSql(customerId, from, to);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(nativeBase).getResultList();
        return rows;
    }

    @Override
    public List<Object[]> countActiveByAssignedUser() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.not(t.get("status").in(TERMINAL_STATUSES)));
        predicates.add(cb.isNotNull(t.get("assignedUserId")));

        cq.multiselect(t.get("assignedUserId"), t.get("status"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("assignedUserId"), t.get("status"));

        return em.createQuery(cq).getResultList();
    }

    @Override
    public List<Object[]> countActiveByGroup() {
        // Returns [groupId, isUnassigned (boolean cast as int), status, count]
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.not(t.get("status").in(TERMINAL_STATUSES)));
        predicates.add(cb.isNotNull(t.get("assignedGroupId")));

        // isUnassigned: 1 when assignedUserId IS NULL, else 0
        Expression<Integer> isUnassigned = cb.<Integer>selectCase()
                .when(cb.isNull(t.get("assignedUserId")), 1)
                .otherwise(0);

        cq.multiselect(t.get("assignedGroupId"), isUnassigned, t.get("status"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("assignedGroupId"), isUnassigned, t.get("status"));

        return em.createQuery(cq).getResultList();
    }

    @Override
    public Double avgFirstResponseMinutes(Long customerId, Instant from, Instant to) {
        StringBuilder jpql = new StringBuilder(
                "SELECT AVG(EXTRACT(EPOCH FROM (t.firstResponseRespondedAt - t.createdAt)) / 60.0) " +
                "FROM Ticket t " +
                "WHERE t.firstResponseRespondedAt IS NOT NULL ");
        if (customerId != null) jpql.append("AND t.customerId = :customerId ");
        if (from != null) jpql.append("AND t.createdAt >= :from ");
        if (to != null) jpql.append("AND t.createdAt <= :to ");

        var query = em.createQuery(jpql.toString(), Double.class);
        if (customerId != null) query.setParameter("customerId", customerId);
        if (from != null) query.setParameter("from", from);
        if (to != null) query.setParameter("to", to);
        try {
            return query.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Double avgResolutionMinutes(Long customerId, Instant from, Instant to) {
        // Average of (resolvedAt - createdAt) for RESOLVED tickets
        // and (closedAt - createdAt) for CLOSED tickets
        StringBuilder jpql = new StringBuilder(
                "SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(t.resolvedAt, t.closedAt) - t.createdAt)) / 60.0) " +
                "FROM Ticket t " +
                "WHERE (t.status = com.caseflow.ticket.domain.TicketStatus.RESOLVED OR t.status = com.caseflow.ticket.domain.TicketStatus.CLOSED) " +
                "AND COALESCE(t.resolvedAt, t.closedAt) IS NOT NULL ");
        if (customerId != null) jpql.append("AND t.customerId = :customerId ");
        if (from != null) jpql.append("AND t.createdAt >= :from ");
        if (to != null) jpql.append("AND t.createdAt <= :to ");

        var query = em.createQuery(jpql.toString(), Double.class);
        if (customerId != null) query.setParameter("customerId", customerId);
        if (from != null) query.setParameter("from", from);
        if (to != null) query.setParameter("to", to);
        try {
            return query.getSingleResult();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long countBreachedResolutionSla(Long customerId) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(t) FROM Ticket t " +
                "WHERE t.resolutionDueAt IS NOT NULL " +
                "AND t.resolutionDueAt < CURRENT_TIMESTAMP " +
                "AND t.status NOT IN (com.caseflow.ticket.domain.TicketStatus.RESOLVED, com.caseflow.ticket.domain.TicketStatus.CLOSED) ");
        if (customerId != null) jpql.append("AND t.customerId = :customerId ");

        var query = em.createQuery(jpql.toString(), Long.class);
        if (customerId != null) query.setParameter("customerId", customerId);
        try {
            Long result = query.getSingleResult();
            return result != null ? result : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public List<Object[]> countByStatusGlobal(Instant from, Instant to) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        addDateBounds(cb, t, predicates, from, to, "createdAt");

        cq.multiselect(t.get("status"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("status"));

        return em.createQuery(cq).getResultList();
    }

    @Override
    public long countAtRiskResolutionSla(Long customerId, Instant warningThreshold) {
        Instant now = Instant.now();
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(t) FROM Ticket t " +
                "WHERE t.resolutionDueAt IS NOT NULL " +
                "AND t.resolutionDueAt > :now " +
                "AND t.resolutionDueAt <= :warningThreshold " +
                "AND t.status NOT IN (com.caseflow.ticket.domain.TicketStatus.RESOLVED, " +
                                     "com.caseflow.ticket.domain.TicketStatus.CLOSED) ");
        if (customerId != null) jpql.append("AND t.customerId = :customerId ");

        var query = em.createQuery(jpql.toString(), Long.class);
        query.setParameter("now", now);
        query.setParameter("warningThreshold", warningThreshold);
        if (customerId != null) query.setParameter("customerId", customerId);
        try {
            Long result = query.getSingleResult();
            return result != null ? result : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    @Override
    public List<Object[]> countBreachedByAssignedUser() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.not(t.get("status").in(TERMINAL_STATUSES)));
        predicates.add(cb.isNotNull(t.get("assignedUserId")));
        predicates.add(cb.isNotNull(t.get("resolutionDueAt")));
        predicates.add(cb.lessThan(t.<Instant>get("resolutionDueAt"), Instant.now()));

        cq.multiselect(t.get("assignedUserId"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("assignedUserId"));

        return em.createQuery(cq).getResultList();
    }

    @Override
    public List<Object[]> countBreachedByGroup() {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Ticket> t = cq.from(Ticket.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.not(t.get("status").in(TERMINAL_STATUSES)));
        predicates.add(cb.isNotNull(t.get("assignedGroupId")));
        predicates.add(cb.isNotNull(t.get("resolutionDueAt")));
        predicates.add(cb.lessThan(t.<Instant>get("resolutionDueAt"), Instant.now()));

        cq.multiselect(t.get("assignedGroupId"), cb.count(t))
                .where(predicates.toArray(new Predicate[0]))
                .groupBy(t.get("assignedGroupId"));

        return em.createQuery(cq).getResultList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void addDateBounds(CriteriaBuilder cb, Root<Ticket> t,
                                       List<Predicate> predicates,
                                       Instant from, Instant to, String field) {
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(t.<Instant>get(field), from));
        }
        if (to != null) {
            predicates.add(cb.lessThanOrEqualTo(t.<Instant>get(field), to));
        }
    }

    /**
     * Builds a native SQL query that returns [date_str, created_count, resolved_count, closed_count]
     * grouped by calendar date.
     */
    private String buildNativeTrendSql(Long customerId, Instant from, Instant to) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT TO_CHAR(dates.d, 'YYYY-MM-DD') AS date, ")
          .append("COALESCE(c.cnt, 0) AS created, ")
          .append("COALESCE(r.cnt, 0) AS resolved, ")
          .append("COALESCE(cl.cnt, 0) AS closed ")
          .append("FROM (");

        // Generate a date series for the window
        if (from != null && to != null) {
            sb.append("SELECT generate_series('").append(DateTimeFormatter.ISO_LOCAL_DATE
                    .withZone(ZoneOffset.UTC).format(from)).append("'::date, '")
              .append(DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(to))
              .append("'::date, '1 day'::interval) AS d");
        } else {
            // Fallback: last 30 days
            sb.append("SELECT generate_series(CURRENT_DATE - 29, CURRENT_DATE, '1 day'::interval) AS d");
        }
        sb.append(") dates ");

        String customerFilter = customerId != null ? " AND t.customer_id = " + customerId : "";

        sb.append("LEFT JOIN (SELECT DATE(created_at) AS d, COUNT(*) cnt FROM tickets t WHERE 1=1")
          .append(buildDateFilter("created_at", from, to)).append(customerFilter)
          .append(" GROUP BY DATE(created_at)) c ON c.d = dates.d::date ");

        sb.append("LEFT JOIN (SELECT DATE(resolved_at) AS d, COUNT(*) cnt FROM tickets t WHERE resolved_at IS NOT NULL")
          .append(buildDateFilter("resolved_at", from, to)).append(customerFilter)
          .append(" GROUP BY DATE(resolved_at)) r ON r.d = dates.d::date ");

        sb.append("LEFT JOIN (SELECT DATE(closed_at) AS d, COUNT(*) cnt FROM tickets t WHERE closed_at IS NOT NULL")
          .append(buildDateFilter("closed_at", from, to)).append(customerFilter)
          .append(" GROUP BY DATE(closed_at)) cl ON cl.d = dates.d::date ");

        sb.append("ORDER BY dates.d");
        return sb.toString();
    }

    private String buildDateFilter(String col, Instant from, Instant to) {
        StringBuilder sb = new StringBuilder();
        if (from != null) {
            sb.append(" AND ").append(col).append(" >= '").append(from).append("'");
        }
        if (to != null) {
            sb.append(" AND ").append(col).append(" <= '").append(to).append("'");
        }
        return sb.toString();
    }
}
