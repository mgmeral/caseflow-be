package com.caseflow.sla.service;

import com.caseflow.common.exception.SlaPolicyNotFoundException;
import com.caseflow.sla.api.dto.SlaPolicyRequest;
import com.caseflow.sla.api.dto.SlaPolicyResponse;
import com.caseflow.sla.api.dto.SlaSummary;
import com.caseflow.sla.domain.SlaPolicyConfig;
import com.caseflow.sla.domain.SlaScope;
import com.caseflow.sla.domain.SlaState;
import com.caseflow.sla.repository.SlaPolicyConfigRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * SLA computation and policy management service.
 *
 * <h2>Policy resolution order (highest priority first)</h2>
 * <ol>
 *   <li>PRIORITY scope matching the ticket's priority</li>
 *   <li>GLOBAL fallback</li>
 * </ol>
 *
 * <h2>First-response rule</h2>
 * First-response is met when {@code ticket.firstResponseRespondedAt != null}.
 * This field is set by {@link com.caseflow.email.scheduler.OutboundDispatchScheduler}
 * on the first confirmed SMTP-sent dispatch for the ticket.
 *
 * <h2>Resolution rule</h2>
 * Resolution is met when the ticket reaches RESOLVED or CLOSED status.
 * The resolution timestamp used is {@code ticket.resolvedAt} (RESOLVED state)
 * or {@code ticket.closedAt} (CLOSED state).
 *
 * <h2>SLA state precedence</h2>
 * BREACHED > WARNING > PAUSED > OK > RESOLVED
 */
@Service
public class SlaService {

    private static final Logger log = LoggerFactory.getLogger(SlaService.class);

    private static final java.util.Set<TicketStatus> TERMINAL_STATUSES =
            java.util.Set.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

    private final SlaPolicyConfigRepository policyRepository;

    public SlaService(SlaPolicyConfigRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    // ── Policy CRUD ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> findAll() {
        return policyRepository.findAllByOrderByScopeAscPriorityAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SlaPolicyResponse findById(Long id) {
        return toResponse(requirePolicy(id));
    }

    @Transactional
    public SlaPolicyResponse create(SlaPolicyRequest request) {
        SlaPolicyConfig policy = new SlaPolicyConfig();
        applyRequest(policy, request);
        SlaPolicyConfig saved = policyRepository.save(policy);
        log.info("SLA_POLICY create — id: {}, scope: {}, priority: {}",
                saved.getId(), saved.getScope(), saved.getPriority());
        return toResponse(saved);
    }

    @Transactional
    public SlaPolicyResponse update(Long id, SlaPolicyRequest request) {
        SlaPolicyConfig policy = requirePolicy(id);
        applyRequest(policy, request);
        SlaPolicyConfig saved = policyRepository.save(policy);
        log.info("SLA_POLICY update — id: {}, scope: {}, priority: {}",
                saved.getId(), saved.getScope(), saved.getPriority());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        SlaPolicyConfig policy = requirePolicy(id);
        policyRepository.delete(policy);
        log.info("SLA_POLICY delete — id: {}", id);
    }

    // ── SLA calculation ───────────────────────────────────────────────────────

    /**
     * Resolves the applicable SLA policy for a ticket based on its priority.
     * Returns the active PRIORITY-scope policy for the ticket's priority, or
     * falls back to the active GLOBAL policy.
     *
     * @param priorityName the {@link com.caseflow.ticket.domain.TicketPriority} name
     * @return matching active policy, or empty if none configured
     */
    @Transactional(readOnly = true)
    public Optional<SlaPolicyConfig> resolvePolicy(String priorityName) {
        // Priority-specific policy takes precedence
        if (priorityName != null) {
            Optional<SlaPolicyConfig> priorityPolicy =
                    policyRepository.findByScopeAndPriorityAndIsActiveTrue(
                            SlaScope.PRIORITY, priorityName);
            if (priorityPolicy.isPresent()) return priorityPolicy;
        }
        // Global fallback
        return policyRepository.findByScopeAndIsActiveTrue(SlaScope.GLOBAL);
    }

    /**
     * Computes the SLA due dates for a ticket at creation time and returns
     * the timestamps to set on the Ticket entity.
     * Call this when creating a new ticket to stamp the SLA targets.
     *
     * @param ticket   the newly created ticket
     * @param createdAt the ticket creation time
     * @return two-element array: [firstResponseDueAt, resolutionDueAt], both may be null
     */
    public Instant[] computeDueDates(Ticket ticket, Instant createdAt) {
        Optional<SlaPolicyConfig> policyOpt = resolvePolicy(
                ticket.getPriority() != null ? ticket.getPriority().name() : null);
        if (policyOpt.isEmpty()) return new Instant[]{null, null};
        SlaPolicyConfig policy = policyOpt.get();
        Instant firstResponseDueAt = createdAt.plus(policy.getFirstResponseTargetMinutes(), ChronoUnit.MINUTES);
        Instant resolutionDueAt = createdAt.plus(policy.getResolutionTargetMinutes(), ChronoUnit.MINUTES);
        return new Instant[]{firstResponseDueAt, resolutionDueAt};
    }

    /**
     * Computes the full SLA summary for a ticket at the current instant.
     * Pure computation — no DB writes.
     *
     * @param ticket the ticket to evaluate
     * @return computed {@link SlaSummary}
     */
    public SlaSummary computeSummary(Ticket ticket) {
        Instant now = Instant.now();
        Instant createdAt = ticket.getCreatedAt();
        Instant statusChangedAt = ticket.getStatusChangedAt() != null
                ? ticket.getStatusChangedAt() : createdAt;

        long ageMinutes = ChronoUnit.MINUTES.between(createdAt, now);
        long currentStatusAgeMinutes = ChronoUnit.MINUTES.between(statusChangedAt, now);

        Instant firstResponseDueAt = ticket.getFirstResponseDueAt();
        Instant resolutionDueAt = ticket.getResolutionDueAt();
        Instant respondedAt = ticket.getFirstResponseRespondedAt();

        boolean isTerminal = TERMINAL_STATUSES.contains(ticket.getStatus());
        boolean isPaused = ticket.getStatus() == TicketStatus.WAITING_CUSTOMER;

        // First-response breach: due date passed and no reply yet sent
        boolean firstResponseBreached = firstResponseDueAt != null
                && respondedAt == null
                && now.isAfter(firstResponseDueAt);

        // Resolution breach: due date passed and ticket is still open
        Instant effectiveResolutionTime = resolveTerminalTime(ticket);
        boolean resolutionBreached = resolutionDueAt != null
                && !isTerminal
                && now.isAfter(resolutionDueAt);

        // Compute composite SLA state
        SlaState state = computeState(ticket, now, firstResponseDueAt, resolutionDueAt,
                respondedAt, isTerminal, isPaused, firstResponseBreached, resolutionBreached);

        return new SlaSummary(
                firstResponseDueAt,
                resolutionDueAt,
                respondedAt,
                firstResponseBreached,
                resolutionBreached,
                state,
                ageMinutes,
                currentStatusAgeMinutes
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SlaState computeState(Ticket ticket, Instant now,
                                   Instant firstResponseDueAt, Instant resolutionDueAt,
                                   Instant respondedAt,
                                   boolean isTerminal, boolean isPaused,
                                   boolean firstResponseBreached, boolean resolutionBreached) {
        // Terminal tickets: SLA clock has stopped
        if (isTerminal) return SlaState.RESOLVED;

        // Either dimension breached → BREACHED
        if (firstResponseBreached || resolutionBreached) return SlaState.BREACHED;

        // Check warning threshold
        if (inWarningWindow(firstResponseDueAt, respondedAt, now)
                || inWarningWindow(resolutionDueAt, null, now)) {
            return SlaState.WARNING;
        }

        // WAITING_CUSTOMER is paused (SLA clock conceptually suspended)
        if (isPaused) return SlaState.PAUSED;

        return SlaState.OK;
    }

    /**
     * Returns true when the given due date is within the warning window AND the target
     * has not yet been met.
     */
    private boolean inWarningWindow(Instant dueAt, Instant metAt, Instant now) {
        if (dueAt == null) return false;
        if (metAt != null) return false; // already met — not in warning
        // Find the applicable policy's warning window from the first active GLOBAL policy
        // (conservative approximation — precise policy would require re-resolving)
        long warningMinutes = policyRepository.findByScopeAndIsActiveTrue(SlaScope.GLOBAL)
                .map(SlaPolicyConfig::getWarningBeforeBreachMinutes).orElse(15);
        Instant warningStart = dueAt.minus(warningMinutes, ChronoUnit.MINUTES);
        return !now.isBefore(warningStart) && now.isBefore(dueAt);
    }

    private Instant resolveTerminalTime(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.RESOLVED && ticket.getResolvedAt() != null) {
            return ticket.getResolvedAt();
        }
        if (ticket.getStatus() == TicketStatus.CLOSED && ticket.getClosedAt() != null) {
            return ticket.getClosedAt();
        }
        return null;
    }

    private SlaPolicyConfig requirePolicy(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new SlaPolicyNotFoundException(id));
    }

    private void applyRequest(SlaPolicyConfig policy, SlaPolicyRequest request) {
        policy.setName(request.name());
        policy.setScope(request.scope());
        policy.setPriority(request.priority());
        policy.setGroupId(request.groupId());
        policy.setFirstResponseTargetMinutes(request.firstResponseTargetMinutes());
        policy.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        policy.setWarningBeforeBreachMinutes(request.warningBeforeBreachMinutes());
        if (request.isActive() != null) policy.setActive(request.isActive());
    }

    private SlaPolicyResponse toResponse(SlaPolicyConfig policy) {
        return new SlaPolicyResponse(
                policy.getId(),
                policy.getName(),
                policy.getScope(),
                policy.getPriority(),
                policy.getGroupId(),
                policy.getFirstResponseTargetMinutes(),
                policy.getResolutionTargetMinutes(),
                policy.getWarningBeforeBreachMinutes(),
                policy.isActive(),
                policy.getCreatedAt(),
                policy.getUpdatedAt()
        );
    }
}
