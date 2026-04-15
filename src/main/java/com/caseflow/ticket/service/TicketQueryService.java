package com.caseflow.ticket.service;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketSlaFilter;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.repository.TicketSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TicketQueryService {

    private final TicketRepository ticketRepository;

    public TicketQueryService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public Ticket getById(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    @Transactional(readOnly = true)
    public Ticket getByTicketNo(String ticketNo) {
        return ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new TicketNotFoundException(ticketNo));
    }

    @Transactional(readOnly = true)
    public Ticket getByPublicId(UUID publicId) {
        return ticketRepository.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException("publicId=" + publicId));
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByStatus(TicketStatus status) {
        return ticketRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByAssignedUser(Long userId) {
        return ticketRepository.findByAssignedUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByAssignedGroup(Long groupId) {
        return ticketRepository.findByAssignedGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> listByCustomer(Long customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> findAll() {
        return ticketRepository.findAll();
    }

    /**
     * Composable search with optional scope enforcement.
     * {@code scopeSpec} is ANDed in when non-null; pass null for no scope restriction.
     *
     * <p>{@code openOnly} restricts to non-terminal statuses (matches dashboard open definition).
     * {@code unassignedOnly} restricts to tickets with no assigned user.
     * {@code tagId}/{@code tagCode} filter to tickets carrying the given tag.
     * {@code staleOpenThreshold} — when non-null, restricts to open tickets where
     *     COALESCE(statusChangedAt, createdAt) < threshold. Used for dashboard drill-down
     *     on the {@code waitingOver24h} metric (pass {@code Instant.now().minus(24, HOURS)}).
     *     Automatically ANDs in {@link TicketSpecification#isOpen()} when applied.
     * {@code slaFilter} — when non-null, applies the exact SLA predicate that matches the
     *     corresponding dashboard metric: {@code BREACHED} maps to {@code breachedSlaCount},
     *     {@code AT_RISK} maps to {@code atRiskSlaCount}. Both predicates include the
     *     non-terminal guard — no additional {@code openOnly} is needed.
     */
    @Transactional(readOnly = true)
    public Page<Ticket> search(TicketStatus status, TicketPriority priority,
                               Long assignedUserId, Long assignedGroupId, Long customerId,
                               String searchText, Instant from, Instant to,
                               Boolean openOnly, Boolean unassignedOnly,
                               Long tagId, String tagCode,
                               Instant staleOpenThreshold,
                               TicketSlaFilter slaFilter,
                               Specification<Ticket> scopeSpec, Pageable pageable) {
        Specification<Ticket> spec = Specification.where(TicketSpecification.hasStatus(status))
                .and(TicketSpecification.hasPriority(priority))
                .and(TicketSpecification.hasAssignedUserId(assignedUserId))
                .and(TicketSpecification.hasAssignedGroupId(assignedGroupId))
                .and(TicketSpecification.hasCustomerId(customerId))
                .and(TicketSpecification.subjectOrTicketNoContains(searchText))
                .and(TicketSpecification.createdAfter(from))
                .and(TicketSpecification.createdBefore(to))
                .and(Boolean.TRUE.equals(openOnly) ? TicketSpecification.isOpen() : null)
                .and(Boolean.TRUE.equals(unassignedOnly) ? TicketSpecification.isUnassigned() : null)
                .and(TicketSpecification.hasTagId(tagId))
                .and(TicketSpecification.hasTagCode(tagCode))
                // staleOpenThreshold: implies isOpen() — only non-terminal tickets can be stale
                .and(staleOpenThreshold != null ? TicketSpecification.isOpen() : null)
                .and(TicketSpecification.statusChangedBefore(staleOpenThreshold))
                .and(buildSlaFilterSpec(slaFilter))
                .and(scopeSpec);

        return ticketRepository.findAll(spec, pageable);
    }

    /**
     * Translates a {@link TicketSlaFilter} into the exact Specification predicate used by
     * the dashboard count — guaranteeing count/list parity.
     * Returns null when {@code slaFilter} is null (no predicate applied).
     */
    private static Specification<Ticket> buildSlaFilterSpec(TicketSlaFilter slaFilter) {
        if (slaFilter == null) return null;
        Instant now = Instant.now();
        return switch (slaFilter) {
            case BREACHED -> TicketSpecification.hasSlaBreached(now);
            case AT_RISK  -> TicketSpecification.hasSlaAtRisk(now, now.plus(TicketSlaFilter.AT_RISK_WINDOW));
        };
    }

    /** Run an arbitrary pre-built specification with pagination — used for admin pool. */
    @Transactional(readOnly = true)
    public Page<Ticket> searchWithSpec(Specification<Ticket> spec, Pageable pageable) {
        return ticketRepository.findAll(spec, pageable);
    }
}
