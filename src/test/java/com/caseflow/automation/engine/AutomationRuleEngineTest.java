package com.caseflow.automation.engine;

import com.caseflow.automation.domain.AutomationRule;
import com.caseflow.automation.domain.AutomationTriggerType;
import com.caseflow.automation.repository.AutomationRuleRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationRuleEngineTest {

    @Mock private AutomationRuleRepository ruleRepository;
    @Mock private TicketRepository ticketRepository;
    @Spy  private ObjectMapper objectMapper;

    @InjectMocks
    private AutomationRuleEngine engine;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = new Ticket();
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setAssignedGroupId(null);
    }

    // ── No active rules — nothing executed ────────────────────────────────────

    @Test
    void evaluate_noActiveRules_doesNothing() {
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(any()))
                .thenReturn(List.of());

        engine.evaluate(AutomationTriggerType.TICKET_CREATED, ticket);

        verify(ticketRepository, never()).save(any());
    }

    // ── Condition matching ─────────────────────────────────────────────────────

    @Test
    void conditionsMatch_noConditionJson_alwaysTrue() {
        AutomationRule rule = rule(null, "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isTrue();
    }

    @Test
    void conditionsMatch_emptyArray_alwaysTrue() {
        AutomationRule rule = rule("[]", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isTrue();
    }

    @Test
    void conditionsMatch_eqCondition_matchesCorrectly() {
        AutomationRule rule = rule(
                "[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"HIGH\"}]", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isTrue();
    }

    @Test
    void conditionsMatch_eqCondition_noMatchOnWrongValue() {
        AutomationRule rule = rule(
                "[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"LOW\"}]", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isFalse();
    }

    @Test
    void conditionsMatch_neqCondition_matches() {
        AutomationRule rule = rule(
                "[{\"field\":\"priority\",\"op\":\"neq\",\"value\":\"LOW\"}]", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isTrue();
    }

    @Test
    void conditionsMatch_isNull_matchesUnassigned() {
        AutomationRule rule = rule(
                "[{\"field\":\"assignedGroupId\",\"op\":\"is_null\"}]", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isTrue();
    }

    @Test
    void conditionsMatch_notNull_falseWhenFieldNull() {
        AutomationRule rule = rule(
                "[{\"field\":\"assignedGroupId\",\"op\":\"not_null\"}]", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isFalse();
    }

    @Test
    void conditionsMatch_multipleConditions_allMustMatch() {
        AutomationRule rule = rule(
                "[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"HIGH\"}," +
                " {\"field\":\"status\",\"op\":\"eq\",\"value\":\"IN_PROGRESS\"}]", "[]");
        // status is NEW, so second condition fails
        assertThat(engine.conditionsMatch(rule, ticket)).isFalse();
    }

    @Test
    void conditionsMatch_invalidJson_returnsFalse() {
        AutomationRule rule = rule("not-json", "[]");
        assertThat(engine.conditionsMatch(rule, ticket)).isFalse();
    }

    // ── Action execution ───────────────────────────────────────────────────────

    @Test
    void evaluate_setPriority_updatesTicket() {
        AutomationRule rule = activeRule(
                "[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"HIGH\"}]",
                "[{\"type\":\"SET_PRIORITY\",\"priority\":\"CRITICAL\"}]",
                AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(
                AutomationTriggerType.TICKET_CREATED)).thenReturn(List.of(rule));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        engine.evaluate(AutomationTriggerType.TICKET_CREATED, ticket);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(TicketPriority.CRITICAL);
    }

    @Test
    void evaluate_assignGroup_setsGroupId() {
        AutomationRule rule = activeRule(
                null,
                "[{\"type\":\"ASSIGN_GROUP\",\"groupId\":7}]",
                AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(
                AutomationTriggerType.TICKET_CREATED)).thenReturn(List.of(rule));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        engine.evaluate(AutomationTriggerType.TICKET_CREATED, ticket);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedGroupId()).isEqualTo(7L);
    }

    @Test
    void evaluate_conditionNotMatched_noSave() {
        AutomationRule rule = activeRule(
                "[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"LOW\"}]",
                "[{\"type\":\"ASSIGN_GROUP\",\"groupId\":7}]",
                AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(
                AutomationTriggerType.TICKET_CREATED)).thenReturn(List.of(rule));

        engine.evaluate(AutomationTriggerType.TICKET_CREATED, ticket);

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void evaluate_badActionJson_doesNotThrow() {
        AutomationRule rule = activeRule(null, "NOT_JSON", AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(any()))
                .thenReturn(List.of(rule));

        assertThatNoException().isThrownBy(
                () -> engine.evaluate(AutomationTriggerType.TICKET_CREATED, ticket));
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void evaluate_ruleThrows_doesNotPropagateToOtherRules() {
        AutomationRule bad = activeRule(null, "NOT_JSON", AutomationTriggerType.TICKET_CREATED);
        AutomationRule good = activeRule(null,
                "[{\"type\":\"SET_PRIORITY\",\"priority\":\"MEDIUM\"}]",
                AutomationTriggerType.TICKET_CREATED);
        when(ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(any()))
                .thenReturn(List.of(bad, good));
        when(ticketRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThatNoException().isThrownBy(
                () -> engine.evaluate(AutomationTriggerType.TICKET_CREATED, ticket));
        verify(ticketRepository).save(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AutomationRule rule(String conditionJson, String actionJson) {
        AutomationRule r = new AutomationRule();
        r.setName("test-rule");
        r.setConditionJson(conditionJson);
        r.setActionJson(actionJson);
        r.setActive(false);
        r.setTriggerType(AutomationTriggerType.TICKET_CREATED);
        return r;
    }

    private AutomationRule activeRule(String conditionJson, String actionJson,
                                      AutomationTriggerType trigger) {
        AutomationRule r = rule(conditionJson, actionJson);
        r.setActive(true);
        r.setTriggerType(trigger);
        return r;
    }
}
