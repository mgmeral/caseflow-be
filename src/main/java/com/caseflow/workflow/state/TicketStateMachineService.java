package com.caseflow.workflow.state;

import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.ticket.domain.TicketStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
public class TicketStateMachineService {

    private static final Map<TicketStatus, Set<TicketStatus>> VALID_TRANSITIONS = new EnumMap<>(TicketStatus.class);

    static {
        VALID_TRANSITIONS.put(TicketStatus.NEW,              EnumSet.of(TicketStatus.TRIAGED));
        VALID_TRANSITIONS.put(TicketStatus.TRIAGED,          EnumSet.of(TicketStatus.ASSIGNED));
        VALID_TRANSITIONS.put(TicketStatus.ASSIGNED,         EnumSet.of(TicketStatus.IN_PROGRESS));
        VALID_TRANSITIONS.put(TicketStatus.IN_PROGRESS,      EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED));
        VALID_TRANSITIONS.put(TicketStatus.WAITING_CUSTOMER, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED));
        VALID_TRANSITIONS.put(TicketStatus.RESOLVED,         EnumSet.of(TicketStatus.CLOSED, TicketStatus.REOPENED));
        VALID_TRANSITIONS.put(TicketStatus.CLOSED,           EnumSet.of(TicketStatus.REOPENED));
        VALID_TRANSITIONS.put(TicketStatus.REOPENED,         EnumSet.of(TicketStatus.TRIAGED, TicketStatus.ASSIGNED));
    }

    public boolean canTransition(TicketStatus from, TicketStatus to) {
        return VALID_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TicketStatus.class)).contains(to);
    }

    public void validateTransition(TicketStatus from, TicketStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTicketStateException(from, to);
        }
    }
}
