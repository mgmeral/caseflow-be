package com.caseflow.sla.service;

import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * On-demand SLA due-date backfill for tickets that were created before the
 * SLA assignment fix (where email-ingested tickets bypassed SLA stamping).
 *
 * <p>This service is safe to call multiple times — it only processes tickets
 * that currently have null {@code resolutionDueAt} and are non-terminal.
 *
 * <p>Exposed via {@code POST /api/admin/sla/backfill} for admin-triggered repair.
 * The V35 Flyway migration performs the same operation at deploy time; this endpoint
 * is provided for post-deploy re-runs, e.g. after a policy change.
 */
@Service
public class SlaBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SlaBackfillService.class);

    private static final Set<TicketStatus> TERMINAL_STATUSES =
            Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final SlaService slaService;
    private final TicketRepository ticketRepository;

    public SlaBackfillService(SlaService slaService, TicketRepository ticketRepository) {
        this.slaService = slaService;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Backfills SLA due dates for all non-terminal tickets with null {@code resolutionDueAt}.
     *
     * <p>For each eligible ticket, the applicable SLA policy is resolved using the same
     * logic as at ticket creation ({@link SlaService#computeDueDates}). If no policy
     * matches, the ticket is skipped (not considered an error).
     *
     * @return summary of the backfill operation
     */
    @Transactional
    public BackfillResult backfillMissingDueDates() {
        List<Ticket> eligible = findEligible();
        log.info("SLA_BACKFILL start — {} eligible tickets", eligible.size());

        int stamped = 0;
        int skipped = 0;
        int failed = 0;

        for (Ticket ticket : eligible) {
            try {
                Instant[] dueDates = slaService.computeDueDates(ticket, ticket.getCreatedAt());
                if (dueDates[0] != null || dueDates[1] != null) {
                    ticket.setFirstResponseDueAt(dueDates[0]);
                    ticket.setResolutionDueAt(dueDates[1]);
                    ticketRepository.save(ticket);
                    stamped++;
                } else {
                    // No active policy resolved for this ticket — leave null intentionally
                    skipped++;
                }
            } catch (Exception e) {
                log.warn("SLA_BACKFILL failed for ticketId: {} — {}", ticket.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("SLA_BACKFILL complete — stamped: {}, skipped (no policy): {}, failed: {}",
                stamped, skipped, failed);
        return new BackfillResult(eligible.size(), stamped, skipped, failed);
    }

    private List<Ticket> findEligible() {
        // Non-terminal tickets with null resolutionDueAt
        Specification<Ticket> spec =
                (root, query, cb) -> cb.and(
                        cb.isNull(root.get("resolutionDueAt")),
                        cb.not(root.get("status").in(TERMINAL_STATUSES)));
        return ticketRepository.findAll(spec);
    }

    /**
     * Result summary of a backfill operation.
     *
     * @param totalEligible tickets found with null resolutionDueAt and non-terminal status
     * @param stamped        tickets that received new SLA due dates
     * @param skipped        tickets skipped because no SLA policy resolved (intentional null)
     * @param failed         tickets that encountered an unexpected error during stamping
     */
    public record BackfillResult(
            int totalEligible,
            int stamped,
            int skipped,
            int failed
    ) {}
}
