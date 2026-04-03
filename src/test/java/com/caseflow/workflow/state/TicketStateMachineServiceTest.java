package com.caseflow.workflow.state;

import com.caseflow.common.exception.InvalidTicketStateException;
import com.caseflow.ticket.domain.TicketStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketStateMachineServiceTest {

    private final TicketStateMachineService sut = new TicketStateMachineService();

    // ── Manual transition matrix ──────────────────────────────────────────────

    @Test
    void newCanTransitionToTriagedAssignedInProgressClosed() {
        Set<TicketStatus> allowed = sut.allowedTransitions(TicketStatus.NEW);
        assertThat(allowed).containsExactlyInAnyOrder(
                TicketStatus.TRIAGED, TicketStatus.ASSIGNED,
                TicketStatus.IN_PROGRESS, TicketStatus.CLOSED);
    }

    @Test
    void triagedCanTransitionToAllWorkingStates() {
        Set<TicketStatus> allowed = sut.allowedTransitions(TicketStatus.TRIAGED);
        assertThat(allowed).containsExactlyInAnyOrder(
                TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
                TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED);
    }

    @Test
    void inProgressCanTransitionToWaitingAssignedResolvedClosed() {
        Set<TicketStatus> allowed = sut.allowedTransitions(TicketStatus.IN_PROGRESS);
        assertThat(allowed).containsExactlyInAnyOrder(
                TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED,
                TicketStatus.ASSIGNED, TicketStatus.CLOSED);
    }

    @Test
    void resolvedCanTransitionToInProgressReopenedClosed() {
        Set<TicketStatus> allowed = sut.allowedTransitions(TicketStatus.RESOLVED);
        assertThat(allowed).containsExactlyInAnyOrder(
                TicketStatus.IN_PROGRESS, TicketStatus.REOPENED, TicketStatus.CLOSED);
    }

    @Test
    void closedCanOnlyTransitionToReopened() {
        Set<TicketStatus> allowed = sut.allowedTransitions(TicketStatus.CLOSED);
        assertThat(allowed).containsExactly(TicketStatus.REOPENED);
    }

    @Test
    void reopenedCanTransitionToAssignedInProgressWaitingResolvedClosed() {
        Set<TicketStatus> allowed = sut.allowedTransitions(TicketStatus.REOPENED);
        assertThat(allowed).containsExactlyInAnyOrder(
                TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
                TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED, TicketStatus.CLOSED);
    }

    @Test
    void validateTransition_throwsForInvalidTransition() {
        // NEW cannot go directly to WAITING_CUSTOMER in this matrix (must go through NEW → IN_PROGRESS first)
        // Actually NEW can go to IN_PROGRESS but not WAITING_CUSTOMER directly
        assertThatThrownBy(() -> sut.validateTransition(TicketStatus.NEW, TicketStatus.WAITING_CUSTOMER))
                .isInstanceOf(InvalidTicketStateException.class);
    }

    @Test
    void validateTransition_allowsLegalTransition() {
        // Should not throw
        sut.validateTransition(TicketStatus.NEW, TicketStatus.IN_PROGRESS);
        sut.validateTransition(TicketStatus.RESOLVED, TicketStatus.REOPENED);
        sut.validateTransition(TicketStatus.CLOSED, TicketStatus.REOPENED);
    }

    @Test
    void canTransition_returnsFalseForSameState() {
        assertThat(sut.canTransition(TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS)).isFalse();
    }

    // ── System transitions on inbound email ───────────────────────────────────

    @Test
    void systemTransitionOnInbound_waitingCustomer_yieldsInProgress() {
        Optional<TicketStatus> result = sut.systemTransitionOnInbound(TicketStatus.WAITING_CUSTOMER);
        assertThat(result).contains(TicketStatus.IN_PROGRESS);
    }

    @Test
    void systemTransitionOnInbound_resolved_yieldsReopened() {
        Optional<TicketStatus> result = sut.systemTransitionOnInbound(TicketStatus.RESOLVED);
        assertThat(result).contains(TicketStatus.REOPENED);
    }

    @Test
    void systemTransitionOnInbound_closed_yieldsReopened() {
        Optional<TicketStatus> result = sut.systemTransitionOnInbound(TicketStatus.CLOSED);
        assertThat(result).contains(TicketStatus.REOPENED);
    }

    @Test
    void systemTransitionOnInbound_inProgress_yieldsEmpty() {
        Optional<TicketStatus> result = sut.systemTransitionOnInbound(TicketStatus.IN_PROGRESS);
        assertThat(result).isEmpty();
    }

    @Test
    void systemTransitionOnInbound_new_yieldsEmpty() {
        Optional<TicketStatus> result = sut.systemTransitionOnInbound(TicketStatus.NEW);
        assertThat(result).isEmpty();
    }

    // ── System transitions on outbound reply ──────────────────────────────────

    @Test
    void systemTransitionOnOutbound_new_yieldsWaitingCustomer() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.NEW))
                .contains(TicketStatus.WAITING_CUSTOMER);
    }

    @Test
    void systemTransitionOnOutbound_triaged_yieldsWaitingCustomer() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.TRIAGED))
                .contains(TicketStatus.WAITING_CUSTOMER);
    }

    @Test
    void systemTransitionOnOutbound_assigned_yieldsWaitingCustomer() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.ASSIGNED))
                .contains(TicketStatus.WAITING_CUSTOMER);
    }

    @Test
    void systemTransitionOnOutbound_inProgress_yieldsWaitingCustomer() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.IN_PROGRESS))
                .contains(TicketStatus.WAITING_CUSTOMER);
    }

    @Test
    void systemTransitionOnOutbound_reopened_yieldsWaitingCustomer() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.REOPENED))
                .contains(TicketStatus.WAITING_CUSTOMER);
    }

    @Test
    void systemTransitionOnOutbound_waitingCustomer_yieldsEmpty() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.WAITING_CUSTOMER)).isEmpty();
    }

    @Test
    void systemTransitionOnOutbound_resolved_yieldsEmpty() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.RESOLVED)).isEmpty();
    }

    @Test
    void systemTransitionOnOutbound_closed_yieldsEmpty() {
        assertThat(sut.systemTransitionOnOutbound(TicketStatus.CLOSED)).isEmpty();
    }
}
