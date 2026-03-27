package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
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
}
