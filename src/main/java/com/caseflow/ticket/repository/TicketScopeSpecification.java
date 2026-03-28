package com.caseflow.ticket.repository;

import com.caseflow.identity.domain.TicketScope;
import com.caseflow.ticket.domain.Ticket;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Specification predicates that enforce ticket visibility based on the caller's TicketScope.
 *
 * Scope rules:
 *   ALL               — no restriction; every ticket is visible
 *   OWN_GROUPS        — ticket.assignedGroupId must be in the user's groups
 *   OWN_AND_OWN_GROUPS — ticket.assignedUserId == userId OR assignedGroupId in user's groups
 *   ASSIGNED_ONLY     — ticket.assignedUserId == userId
 *
 * All methods are null-safe: null/empty group lists are treated as no-group membership.
 */
public final class TicketScopeSpecification {

    private TicketScopeSpecification() {}

    /**
     * Limits a search query to tickets the caller is allowed to see.
     * Returns null (= no restriction) for ALL scope so it can be ANDed without effect.
     */
    public static Specification<Ticket> visibleTo(Long userId, List<Long> groupIds, TicketScope scope) {
        return switch (scope) {
            case ALL -> null;
            case OWN_GROUPS -> assignedGroupIn(groupIds);
            case OWN_AND_OWN_GROUPS -> assignedToUserOrGroups(userId, groupIds);
            case ASSIGNED_ONLY -> assignedToUser(userId);
        };
    }

    /**
     * Scope predicate for the admin pool (tickets with no user assignment).
     * Always includes the unassigned-user filter; scope further narrows by group.
     * ASSIGNED_ONLY returns an always-false predicate (no unassigned tickets visible).
     */
    public static Specification<Ticket> adminPoolVisibleTo(Long userId, List<Long> groupIds, TicketScope scope) {
        return switch (scope) {
            case ALL -> unassignedUser();
            case OWN_GROUPS, OWN_AND_OWN_GROUPS -> unassignedUser().and(assignedGroupIn(groupIds));
            case ASSIGNED_ONLY -> (root, query, cb) -> cb.disjunction(); // always false
        };
    }

    /** Tickets with no user assigned — available for pickup. */
    public static Specification<Ticket> unassignedUser() {
        return (root, query, cb) -> cb.isNull(root.get("assignedUserId"));
    }

    private static Specification<Ticket> assignedToUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("assignedUserId"), userId);
    }

    private static Specification<Ticket> assignedGroupIn(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return (root, query, cb) -> cb.disjunction(); // no groups → nothing visible
        }
        return (root, query, cb) -> root.get("assignedGroupId").in(groupIds);
    }

    private static Specification<Ticket> assignedToUserOrGroups(Long userId, List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return assignedToUser(userId);
        }
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("assignedUserId"), userId),
                root.get("assignedGroupId").in(groupIds)
        );
    }
}
