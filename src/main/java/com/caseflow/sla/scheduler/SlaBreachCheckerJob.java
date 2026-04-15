package com.caseflow.sla.scheduler;

import com.caseflow.integration.domain.TicketDomainEvent;
import com.caseflow.integration.notification.domain.NotificationEventType;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.sla.api.dto.SlaSummary;
import com.caseflow.sla.domain.SlaEventLog;
import com.caseflow.sla.domain.SlaState;
import com.caseflow.sla.repository.SlaEventLogRepository;
import com.caseflow.sla.service.SlaService;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Periodic job that evaluates SLA state for open tickets and emits
 * SLA_WARNING, SLA_BREACHED, and SLA_RECOVERED events on state transitions.
 *
 * <h2>Idempotency guarantee</h2>
 * Events and in-app notifications are emitted at most once per state transition.
 * The {@link SlaEventLog} table persists the last emitted state per ticket.
 * Repeated scheduler runs against a ticket already in the same state are skipped.
 *
 * <h2>State machine</h2>
 * <pre>
 *  (no entry) ──▶ WARNING ──▶ BREACHED
 *                   │              │
 *                   └──▶ OK ◀──────┘  ← state improved (SLA extended or ticket paused)
 *                   └──▶ RECOVERED    ← ticket went terminal (RESOLVED/CLOSED)
 *                         │
 *                         └──▶ (entry deleted; next re-open starts fresh)
 * </pre>
 *
 * <h2>Recovery sweep</h2>
 * The job performs a secondary pass over all active log entries and emits
 * SLA_RECOVERED when the referenced ticket is now terminal. This covers cases
 * where a ticket is resolved between scheduler cycles.
 */
@Component
public class SlaBreachCheckerJob {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachCheckerJob.class);

    private static final Set<TicketStatus> TERMINAL_STATUSES =
            Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private static final List<SlaState> ACTIVE_SLA_STATES =
            List.of(SlaState.WARNING, SlaState.BREACHED);

    private final TicketRepository ticketRepository;
    private final SlaService slaService;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final SlaEventLogRepository slaEventLogRepository;

    public SlaBreachCheckerJob(TicketRepository ticketRepository,
                                SlaService slaService,
                                ApplicationEventPublisher eventPublisher,
                                NotificationService notificationService,
                                SlaEventLogRepository slaEventLogRepository) {
        this.ticketRepository = ticketRepository;
        this.slaService = slaService;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.slaEventLogRepository = slaEventLogRepository;
    }

    /**
     * Main SLA evaluation pass: scans open tickets with SLA due dates and emits
     * events only on state transitions (WARNING→BREACHED, (new)→WARNING, etc.).
     *
     * <p>Runs every 5 minutes by default (configurable via {@code caseflow.sla.check-interval-ms}).
     */
    @Scheduled(fixedDelayString = "${caseflow.sla.check-interval-ms:300000}")
    @Transactional
    public void checkBreaches() {
        List<Ticket> candidates = ticketRepository.findAll(hasSlaAndIsOpen());
        if (candidates.isEmpty()) {
            sweepRecoveries();
            return;
        }

        log.debug("SLA_CHECK evaluating {} open tickets", candidates.size());

        // Pre-load all existing log entries for this batch to avoid N+1
        Set<Long> ticketIds = candidates.stream().map(Ticket::getId).collect(Collectors.toSet());
        Map<Long, SlaEventLog> existingEntries = slaEventLogRepository.findAllBySlaStateIn(ACTIVE_SLA_STATES)
                .stream()
                .filter(e -> ticketIds.contains(e.getTicketId()))
                .collect(Collectors.toMap(SlaEventLog::getTicketId, Function.identity()));

        int warnings = 0;
        int breaches = 0;
        int transitions = 0;
        int skipped = 0;

        for (Ticket ticket : candidates) {
            SlaSummary summary = slaService.computeSummary(ticket);
            SlaState currentState = summary.slaState();

            // Only act on actionable states
            if (currentState != SlaState.WARNING && currentState != SlaState.BREACHED) {
                // Ticket improved to OK/PAUSED: clean up any stale log entry
                SlaEventLog existing = existingEntries.get(ticket.getId());
                if (existing != null) {
                    slaEventLogRepository.deleteByTicketId(ticket.getId());
                    log.debug("SLA_LOG cleanup ticketId: {} state improved to {}", ticket.getId(), currentState);
                }
                continue;
            }

            SlaEventLog existing = existingEntries.get(ticket.getId());

            if (existing == null) {
                // First time this ticket enters an at-risk state
                slaEventLogRepository.save(new SlaEventLog(ticket.getId(), currentState));
                emitEvent(ticket, currentState);
                if (currentState == SlaState.BREACHED) breaches++;
                else warnings++;

            } else if (existing.getSlaState() == currentState) {
                // Same state as last emission — skip (idempotency guard)
                skipped++;

            } else {
                // State transition (e.g. WARNING → BREACHED)
                existing.transition(currentState);
                slaEventLogRepository.save(existing);
                emitEvent(ticket, currentState);
                transitions++;
                if (currentState == SlaState.BREACHED) breaches++;
                else warnings++;
            }
        }

        if (warnings > 0 || breaches > 0 || transitions > 0) {
            log.info("SLA_CHECK complete — new warnings: {}, new breaches: {}, transitions: {}, skipped: {}",
                    warnings, breaches, transitions, skipped);
        }

        // Recovery sweep for tickets that went terminal since last run
        sweepRecoveries();
    }

    /**
     * Finds tickets that have an active SLA log entry but are now terminal
     * (RESOLVED or CLOSED), emits SLA_RECOVERED, and removes the log entry.
     */
    private void sweepRecoveries() {
        List<SlaEventLog> activeEntries = slaEventLogRepository.findAllBySlaStateIn(ACTIVE_SLA_STATES);
        if (activeEntries.isEmpty()) return;

        // Fetch the actual tickets to check their current status
        List<Long> loggedTicketIds = activeEntries.stream()
                .map(SlaEventLog::getTicketId).toList();
        Map<Long, Ticket> ticketMap = ticketRepository.findAllById(loggedTicketIds)
                .stream().collect(Collectors.toMap(Ticket::getId, Function.identity()));

        int recovered = 0;
        for (SlaEventLog entry : activeEntries) {
            Ticket ticket = ticketMap.get(entry.getTicketId());
            if (ticket != null && TERMINAL_STATUSES.contains(ticket.getStatus())) {
                // Ticket has been resolved/closed — emit recovery and clean up the log entry
                emitRecoveryEvent(ticket);
                slaEventLogRepository.deleteByTicketId(entry.getTicketId());
                recovered++;
            }
            // If ticket is not in the map (not found by findAllById), we leave the entry alone.
            // The main scan in the next run will clean it up once the ticket re-appears open
            // (or it will be excluded from candidates and stay harmlessly in the log).
        }

        if (recovered > 0) {
            log.info("SLA_RECOVERY sweep — {} tickets recovered", recovered);
        }
    }

    // ── Event emission ────────────────────────────────────────────────────────

    private void emitEvent(Ticket ticket, SlaState state) {
        if (state == SlaState.BREACHED) {
            emitBreachEvent(ticket);
        } else {
            emitWarningEvent(ticket);
        }
    }

    private void emitBreachEvent(Ticket ticket) {
        log.warn("SLA_BREACHED — ticketId: {}, ticketNo: {}, resolutionDue: {}",
                ticket.getId(), ticket.getTicketNo(), ticket.getResolutionDueAt());
        eventPublisher.publishEvent(new TicketDomainEvent(
                ticket.getId(), ticket.getPublicId(),
                NotificationEventType.SLA_BREACHED, null,
                ticket.getCustomerId(), ticket.getAssignedGroupId()));
        if (ticket.getAssignedUserId() != null) {
            notificationService.notifySlaBreached(
                    ticket.getAssignedUserId(),
                    ticket.getId(), ticket.getPublicId(), ticket.getTicketNo());
        }
    }

    private void emitWarningEvent(Ticket ticket) {
        log.info("SLA_WARNING — ticketId: {}, ticketNo: {}", ticket.getId(), ticket.getTicketNo());
        eventPublisher.publishEvent(new TicketDomainEvent(
                ticket.getId(), ticket.getPublicId(),
                NotificationEventType.SLA_WARNING, null,
                ticket.getCustomerId(), ticket.getAssignedGroupId()));
        if (ticket.getAssignedUserId() != null) {
            notificationService.notifySlaWarning(
                    ticket.getAssignedUserId(),
                    ticket.getId(), ticket.getPublicId(), ticket.getTicketNo());
        }
    }

    private void emitRecoveryEvent(Ticket ticket) {
        log.info("SLA_RECOVERED — ticketId: {}, ticketNo: {}, status: {}",
                ticket.getId(), ticket.getTicketNo(), ticket.getStatus());
        eventPublisher.publishEvent(new TicketDomainEvent(
                ticket.getId(), ticket.getPublicId(),
                NotificationEventType.SLA_RECOVERED, null,
                ticket.getCustomerId(), ticket.getAssignedGroupId()));
        if (ticket.getAssignedUserId() != null) {
            notificationService.notifySlaRecovered(
                    ticket.getAssignedUserId(),
                    ticket.getId(), ticket.getPublicId(), ticket.getTicketNo());
        }
    }

    /**
     * Specification: open ticket with at least one SLA due date set.
     */
    private static Specification<Ticket> hasSlaAndIsOpen() {
        return (root, query, cb) -> cb.and(
                cb.not(root.get("status").in(TERMINAL_STATUSES)),
                cb.or(
                        cb.isNotNull(root.get("firstResponseDueAt")),
                        cb.isNotNull(root.get("resolutionDueAt"))
                )
        );
    }
}
