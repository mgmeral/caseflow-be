package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Tag;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.domain.TicketTag;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class TicketSpecification {

    private TicketSpecification() {}

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasPriority(TicketPriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> hasAssignedUserId(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("assignedUserId"), userId);
    }

    public static Specification<Ticket> hasAssignedGroupId(Long groupId) {
        return (root, query, cb) -> groupId == null ? null : cb.equal(root.get("assignedGroupId"), groupId);
    }

    public static Specification<Ticket> hasCustomerId(Long customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Ticket> subjectOrTicketNoContains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String like = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("subject")), like),
                    cb.like(cb.lower(root.get("ticketNo")), like)
            );
        };
    }

    public static Specification<Ticket> createdAfter(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Ticket> createdBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    /**
     * Tickets in any non-terminal status (NEW, TRIAGED, ASSIGNED, IN_PROGRESS,
     * WAITING_CUSTOMER, REOPENED). Matches the dashboard "open" definition.
     */
    public static Specification<Ticket> isOpen() {
        return (root, query, cb) ->
                cb.not(root.get("status").in(TicketStatus.RESOLVED, TicketStatus.CLOSED));
    }

    /**
     * Tickets with no assigned user. Matches the dashboard "unassigned active" predicate
     * (callers should AND this with {@link #isOpen()} when reproducing that dashboard metric).
     */
    public static Specification<Ticket> isUnassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignedUserId"));
    }

    /**
     * Tickets that carry the given tag ID. Uses an EXISTS subquery on {@code ticket_tags}.
     */
    public static Specification<Ticket> hasTagId(Long tagId) {
        return (root, query, cb) -> {
            if (tagId == null) return null;
            Subquery<Long> sub = query.subquery(Long.class);
            Root<TicketTag> tt = sub.from(TicketTag.class);
            sub.select(tt.get("ticketId"))
               .where(cb.and(
                   cb.equal(tt.get("ticketId"), root.get("id")),
                   cb.equal(tt.get("tagId"), tagId)));
            return cb.exists(sub);
        };
    }

    /**
     * Tickets that carry the tag with the given code (case-insensitive; normalised to upper-case
     * for storage). Uses a nested EXISTS subquery: ticket_tags → tags.
     */
    public static Specification<Ticket> hasTagCode(String tagCode) {
        return (root, query, cb) -> {
            if (tagCode == null || tagCode.isBlank()) return null;
            // Sub-select: tag IDs matching the given code
            Subquery<Long> tagIdSub = query.subquery(Long.class);
            Root<Tag> tagRoot = tagIdSub.from(Tag.class);
            tagIdSub.select(tagRoot.get("id"))
                    .where(cb.equal(tagRoot.get("code"), tagCode.toUpperCase()));
            // Exists sub-select: ticket has a ticket_tags row for one of those tag IDs
            Subquery<Long> ttSub = query.subquery(Long.class);
            Root<TicketTag> tt = ttSub.from(TicketTag.class);
            ttSub.select(tt.get("ticketId"))
                 .where(cb.and(
                     cb.equal(tt.get("ticketId"), root.get("id")),
                     tt.get("tagId").in(tagIdSub)));
            return cb.exists(ttSub);
        };
    }
}
