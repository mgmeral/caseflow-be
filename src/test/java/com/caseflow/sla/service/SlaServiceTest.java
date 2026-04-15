package com.caseflow.sla.service;

import com.caseflow.sla.domain.SlaPolicyConfig;
import com.caseflow.sla.domain.SlaScope;
import com.caseflow.sla.domain.SlaState;
import com.caseflow.sla.repository.SlaPolicyConfigRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock
    private SlaPolicyConfigRepository policyRepository;

    @InjectMocks
    private SlaService sut;

    private SlaPolicyConfig globalPolicy;

    @BeforeEach
    void setUp() {
        globalPolicy = new SlaPolicyConfig();
        globalPolicy.setName("Global");
        globalPolicy.setScope(SlaScope.GLOBAL);
        globalPolicy.setFirstResponseTargetMinutes(60);
        globalPolicy.setResolutionTargetMinutes(480);
        globalPolicy.setWarningBeforeBreachMinutes(15);
        globalPolicy.setActive(true);

        // lenient: not all tests exercise the warning-window path that calls this
        lenient().when(policyRepository.findByScopeAndIsActiveTrue(SlaScope.GLOBAL))
                .thenReturn(Optional.of(globalPolicy));
    }

    // ── computeDueDates ───────────────────────────────────────────────────────

    @Test
    void computeDueDates_stampsCorrectDueDates_fromGlobalPolicy() {
        when(policyRepository.findByScopeAndPriorityAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        Ticket ticket = ticket(TicketStatus.NEW, TicketPriority.MEDIUM);
        Instant now = Instant.now();

        Instant[] dueDates = sut.computeDueDates(ticket, now);

        assertThat(dueDates[0]).isEqualTo(now.plus(60, ChronoUnit.MINUTES));
        assertThat(dueDates[1]).isEqualTo(now.plus(480, ChronoUnit.MINUTES));
    }

    @Test
    void computeDueDates_usesPriorityPolicy_whenAvailable() {
        SlaPolicyConfig highPolicy = new SlaPolicyConfig();
        highPolicy.setScope(SlaScope.PRIORITY);
        highPolicy.setPriority("HIGH");
        highPolicy.setFirstResponseTargetMinutes(30);
        highPolicy.setResolutionTargetMinutes(240);
        highPolicy.setWarningBeforeBreachMinutes(10);
        highPolicy.setActive(true);

        when(policyRepository.findByScopeAndPriorityAndIsActiveTrue(SlaScope.PRIORITY, "HIGH"))
                .thenReturn(Optional.of(highPolicy));

        Ticket ticket = ticket(TicketStatus.NEW, TicketPriority.HIGH);
        Instant now = Instant.now();

        Instant[] dueDates = sut.computeDueDates(ticket, now);

        assertThat(dueDates[0]).isEqualTo(now.plus(30, ChronoUnit.MINUTES));
        assertThat(dueDates[1]).isEqualTo(now.plus(240, ChronoUnit.MINUTES));
    }

    @Test
    void computeDueDates_returnsNulls_whenNoPolicyConfigured() {
        when(policyRepository.findByScopeAndPriorityAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        when(policyRepository.findByScopeAndIsActiveTrue(SlaScope.GLOBAL))
                .thenReturn(Optional.empty());

        Ticket ticket = ticket(TicketStatus.NEW, TicketPriority.LOW);
        Instant[] dueDates = sut.computeDueDates(ticket, Instant.now());

        assertThat(dueDates[0]).isNull();
        assertThat(dueDates[1]).isNull();
    }

    // ── computeSummary / computeState ─────────────────────────────────────────

    @Test
    void computeSummary_returnsOk_whenWithinTargets() {
        Ticket ticket = ticketWithSla(TicketStatus.IN_PROGRESS,
                Instant.now().plus(30, ChronoUnit.MINUTES),   // firstResponseDueAt — in future
                Instant.now().plus(240, ChronoUnit.MINUTES),  // resolutionDueAt — in future
                null);                                          // not yet responded

        var summary = sut.computeSummary(ticket);

        assertThat(summary.slaState()).isEqualTo(SlaState.OK);
        assertThat(summary.firstResponseBreached()).isFalse();
        assertThat(summary.resolutionBreached()).isFalse();
    }

    @Test
    void computeSummary_returnsBreached_whenFirstResponseOverdue() {
        Ticket ticket = ticketWithSla(TicketStatus.IN_PROGRESS,
                Instant.now().minus(5, ChronoUnit.MINUTES),   // firstResponseDueAt — in PAST
                Instant.now().plus(240, ChronoUnit.MINUTES),  // resolutionDueAt — in future
                null);                                          // never responded

        var summary = sut.computeSummary(ticket);

        assertThat(summary.slaState()).isEqualTo(SlaState.BREACHED);
        assertThat(summary.firstResponseBreached()).isTrue();
    }

    @Test
    void computeSummary_returnsBreached_whenResolutionOverdue() {
        Ticket ticket = ticketWithSla(TicketStatus.IN_PROGRESS,
                Instant.now().plus(60, ChronoUnit.MINUTES),   // firstResponseDueAt OK
                Instant.now().minus(10, ChronoUnit.MINUTES),  // resolutionDueAt — PAST
                Instant.now().minus(30, ChronoUnit.MINUTES)); // already responded

        var summary = sut.computeSummary(ticket);

        assertThat(summary.slaState()).isEqualTo(SlaState.BREACHED);
        assertThat(summary.resolutionBreached()).isTrue();
    }

    @Test
    void computeSummary_returnsResolved_forTerminalTicket() {
        Ticket ticket = ticketWithSla(TicketStatus.RESOLVED,
                Instant.now().minus(5, ChronoUnit.MINUTES),  // both breached
                Instant.now().minus(5, ChronoUnit.MINUTES),
                null);

        var summary = sut.computeSummary(ticket);

        // Terminal tickets are always RESOLVED regardless of breach state
        assertThat(summary.slaState()).isEqualTo(SlaState.RESOLVED);
    }

    @Test
    void computeSummary_returnsPaused_forWaitingCustomer() {
        Ticket ticket = ticketWithSla(TicketStatus.WAITING_CUSTOMER,
                Instant.now().plus(30, ChronoUnit.MINUTES),
                Instant.now().plus(120, ChronoUnit.MINUTES),
                Instant.now().minus(10, ChronoUnit.MINUTES)); // responded

        var summary = sut.computeSummary(ticket);

        assertThat(summary.slaState()).isEqualTo(SlaState.PAUSED);
    }

    @Test
    void computeSummary_returnsWarning_whenApproachingDeadline() {
        // Within 15-minute warning window
        Ticket ticket = ticketWithSla(TicketStatus.IN_PROGRESS,
                Instant.now().plus(10, ChronoUnit.MINUTES),  // < 15 min to breach
                Instant.now().plus(120, ChronoUnit.MINUTES),
                null);

        var summary = sut.computeSummary(ticket);

        assertThat(summary.slaState()).isEqualTo(SlaState.WARNING);
    }

    @Test
    void computeSummary_includesAgeMinutes() {
        Ticket ticket = ticket(TicketStatus.NEW, TicketPriority.MEDIUM);
        setCreatedAt(ticket, Instant.now().minus(90, ChronoUnit.MINUTES));

        var summary = sut.computeSummary(ticket);

        assertThat(summary.ageMinutes()).isGreaterThanOrEqualTo(89L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticket ticket(TicketStatus status, TicketPriority priority) {
        Ticket t = new Ticket();
        t.setStatus(status);
        t.setPriority(priority);
        setCreatedAt(t, Instant.now());
        return t;
    }

    private Ticket ticketWithSla(TicketStatus status,
                                   Instant firstResponseDueAt, Instant resolutionDueAt,
                                   Instant firstResponseRespondedAt) {
        Ticket t = ticket(status, TicketPriority.MEDIUM);
        t.setFirstResponseDueAt(firstResponseDueAt);
        t.setResolutionDueAt(resolutionDueAt);
        t.setFirstResponseRespondedAt(firstResponseRespondedAt);
        return t;
    }

    /** Ticket.createdAt has no public setter (set by @PrePersist); use reflection in tests. */
    private static void setCreatedAt(Ticket ticket, Instant value) {
        try {
            var field = Ticket.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(ticket, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
