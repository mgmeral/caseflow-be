package com.caseflow.workflow.state;

import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.ticket.domain.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces the ticket state machine for both manual and system-triggered transitions.
 *
 * <h2>Manual transition matrix</h2>
 * <pre>
 * NEW              → TRIAGED, ASSIGNED, IN_PROGRESS, CLOSED
 * TRIAGED          → ASSIGNED, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED
 * ASSIGNED         → IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED
 * IN_PROGRESS      → WAITING_CUSTOMER, RESOLVED, ASSIGNED, CLOSED
 * WAITING_CUSTOMER → IN_PROGRESS, RESOLVED, CLOSED
 * RESOLVED         → IN_PROGRESS, REOPENED, CLOSED
 * CLOSED           → REOPENED
 * REOPENED         → ASSIGNED, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED
 * </pre>
 *
 * <h2>System-triggered transitions (email events)</h2>
 * <ul>
 *   <li>Inbound customer email on WAITING_CUSTOMER → IN_PROGRESS</li>
 *   <li>Inbound customer email on RESOLVED → REOPENED</li>
 *   <li>Inbound customer email on CLOSED → REOPENED</li>
 *   <li>Outbound agent reply from active work states → WAITING_CUSTOMER</li>
 * </ul>
 */
@Service
public class TicketStateMachineService {

    private static final Logger log = LoggerFactory.getLogger(TicketStateMachineService.class);

    /** States from which an outbound agent reply triggers WAITING_CUSTOMER. */
    private static final Set<TicketStatus> OUTBOUND_TRIGGERS_WAITING =
            EnumSet.of(TicketStatus.NEW, TicketStatus.TRIAGED, TicketStatus.ASSIGNED,
                    TicketStatus.IN_PROGRESS, TicketStatus.REOPENED);

    private static final Map<TicketStatus, Set<TicketStatus>> VALID_TRANSITIONS =
            new EnumMap<>(TicketStatus.class);

    static {
        VALID_TRANSITIONS.put(TicketStatus.NEW,
                EnumSet.of(TicketStatus.TRIAGED, TicketStatus.ASSIGNED,
                        TicketStatus.IN_PROGRESS, TicketStatus.CLOSED));

        VALID_TRANSITIONS.put(TicketStatus.TRIAGED,
                EnumSet.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
                        TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED));

        VALID_TRANSITIONS.put(TicketStatus.ASSIGNED,
                EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER,
                        TicketStatus.RESOLVED, TicketStatus.CLOSED));

        VALID_TRANSITIONS.put(TicketStatus.IN_PROGRESS,
                EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED,
                        TicketStatus.ASSIGNED, TicketStatus.CLOSED));

        VALID_TRANSITIONS.put(TicketStatus.WAITING_CUSTOMER,
                EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED));

        VALID_TRANSITIONS.put(TicketStatus.RESOLVED,
                EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.REOPENED, TicketStatus.CLOSED));

        VALID_TRANSITIONS.put(TicketStatus.CLOSED,
                EnumSet.of(TicketStatus.REOPENED));

        VALID_TRANSITIONS.put(TicketStatus.REOPENED,
                EnumSet.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
                        TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED));
    }

    public boolean canTransition(TicketStatus from, TicketStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)).contains(to);
    }

    public void validateTransition(TicketStatus from, TicketStatus to) {
        if (!canTransition(from, to)) {
            log.warn("Invalid ticket state transition: {} -> {}", from, to);
            throw new InvalidTicketStateException(from, to);
        }
    }

    /**
     * Returns the allowed manual transition targets from the given status.
     * Exposed to the FE via GET /api/tickets/{id}/transitions.
     */
    public Set<TicketStatus> allowedTransitions(TicketStatus from) {
        return VALID_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TicketStatus.class));
    }

    /**
     * Returns the system-triggered target status when a new inbound customer email
     * arrives for a ticket in {@code currentStatus}, or {@link Optional#empty()} if no
     * automatic transition should occur.
     *
     * <ul>
     *   <li>WAITING_CUSTOMER → IN_PROGRESS (customer replied, work resumes)</li>
     *   <li>RESOLVED → REOPENED (customer replied after resolution)</li>
     *   <li>CLOSED → REOPENED (customer reopens a closed ticket)</li>
     * </ul>
     */
    public Optional<TicketStatus> systemTransitionOnInbound(TicketStatus currentStatus) {
        return switch (currentStatus) {
            case WAITING_CUSTOMER -> Optional.of(TicketStatus.IN_PROGRESS);
            case RESOLVED         -> Optional.of(TicketStatus.REOPENED);
            case CLOSED           -> Optional.of(TicketStatus.REOPENED);
            default               -> Optional.empty();
        };
    }

    /**
     * Returns the system-triggered target status when an agent sends an outbound reply
     * for a ticket in {@code currentStatus}, or {@link Optional#empty()} if no transition
     * should occur.
     *
     * <p>Active work states (NEW, TRIAGED, ASSIGNED, IN_PROGRESS, REOPENED) transition to
     * WAITING_CUSTOMER to signal that the ball is in the customer's court.
     * WAITING_CUSTOMER and RESOLVED are left unchanged (already in the right state).
     */
    public Optional<TicketStatus> systemTransitionOnOutbound(TicketStatus currentStatus) {
        if (OUTBOUND_TRIGGERS_WAITING.contains(currentStatus)) {
            return Optional.of(TicketStatus.WAITING_CUSTOMER);
        }
        return Optional.empty();
    }
}
