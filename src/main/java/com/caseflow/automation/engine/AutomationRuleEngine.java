package com.caseflow.automation.engine;

import com.caseflow.automation.domain.AutomationRule;
import com.caseflow.automation.domain.AutomationTriggerType;
import com.caseflow.automation.repository.AutomationRuleRepository;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.repository.TicketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Evaluates active automation rules for a given trigger type against a ticket,
 * then executes the matching rules' actions in order.
 *
 * <p>Each rule is evaluated independently; a failure in one rule does not block
 * subsequent rules. Actions within a single rule share a single repository save.
 */
@Service
public class AutomationRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(AutomationRuleEngine.class);

    private static final TypeReference<List<AutomationCondition>> CONDITION_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<AutomationAction>> ACTION_TYPE =
            new TypeReference<>() {};

    private final AutomationRuleRepository ruleRepository;
    private final TicketRepository ticketRepository;
    private final ObjectMapper objectMapper;

    public AutomationRuleEngine(AutomationRuleRepository ruleRepository,
                                TicketRepository ticketRepository,
                                ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.ticketRepository = ticketRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void evaluate(AutomationTriggerType triggerType, Ticket ticket) {
        List<AutomationRule> rules =
                ruleRepository.findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(triggerType);
        if (rules.isEmpty()) return;

        log.debug("Evaluating {} active rule(s) for trigger {} on ticket {}",
                rules.size(), triggerType, ticket.getId());

        for (AutomationRule rule : rules) {
            try {
                if (conditionsMatch(rule, ticket)) {
                    log.info("Automation rule {} matched ticket {} — executing actions",
                            rule.getId(), ticket.getId());
                    applyActions(rule, ticket);
                }
            } catch (Exception e) {
                log.error("Automation rule {} failed for ticket {}: {}",
                        rule.getId(), ticket.getId(), e.getMessage(), e);
            }
        }
    }

    // ── Condition evaluation ───────────────────────────────────────────────────

    boolean conditionsMatch(AutomationRule rule, Ticket ticket) {
        String json = rule.getConditionJson();
        if (json == null || json.isBlank()) return true;
        try {
            List<AutomationCondition> conditions = objectMapper.readValue(json, CONDITION_TYPE);
            if (conditions.isEmpty()) return true;
            return conditions.stream().allMatch(c -> evaluate(c, ticket));
        } catch (Exception e) {
            log.warn("Rule {} has unparseable conditionJson — skipping: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    private boolean evaluate(AutomationCondition c, Ticket ticket) {
        String actual = resolveField(c.field(), ticket);
        return switch (c.op()) {
            case "eq"       -> c.value() != null && c.value().equals(actual);
            case "neq"      -> !Objects.equals(c.value(), actual);
            case "not_null" -> actual != null;
            case "is_null"  -> actual == null;
            default -> {
                log.warn("Unknown condition op '{}' — treating as false", c.op());
                yield false;
            }
        };
    }

    private String resolveField(String field, Ticket ticket) {
        return switch (field) {
            case "priority"        -> ticket.getPriority()        != null ? ticket.getPriority().name()        : null;
            case "status"          -> ticket.getStatus()          != null ? ticket.getStatus().name()          : null;
            case "assignedGroupId" -> ticket.getAssignedGroupId() != null ? ticket.getAssignedGroupId().toString() : null;
            case "assignedUserId"  -> ticket.getAssignedUserId()  != null ? ticket.getAssignedUserId().toString()  : null;
            case "customerId"      -> ticket.getCustomerId()      != null ? ticket.getCustomerId().toString()      : null;
            default -> {
                log.warn("Unknown condition field '{}' — treating as null", field);
                yield null;
            }
        };
    }

    // ── Action execution ───────────────────────────────────────────────────────

    private void applyActions(AutomationRule rule, Ticket ticket) {
        String json = rule.getActionJson();
        if (json == null || json.isBlank()) return;
        try {
            List<AutomationAction> actions = objectMapper.readValue(json, ACTION_TYPE);
            boolean modified = false;
            for (AutomationAction action : actions) {
                modified |= apply(action, ticket, rule.getId());
            }
            if (modified) {
                ticketRepository.save(ticket);
            }
        } catch (Exception e) {
            log.warn("Rule {} has unparseable actionJson — skipping: {}", rule.getId(), e.getMessage());
        }
    }

    private boolean apply(AutomationAction action, Ticket ticket, Long ruleId) {
        if (action.type() == null) {
            log.warn("Rule {} contains action with null type — skipping", ruleId);
            return false;
        }
        return switch (action.type()) {
            case "SET_PRIORITY" -> {
                if (action.priority() == null) { log.warn("Rule {} SET_PRIORITY missing priority", ruleId); yield false; }
                ticket.setPriority(TicketPriority.valueOf(action.priority()));
                log.debug("Rule {} SET_PRIORITY {} on ticket {}", ruleId, action.priority(), ticket.getId());
                yield true;
            }
            case "SET_STATUS" -> {
                if (action.status() == null) { log.warn("Rule {} SET_STATUS missing status", ruleId); yield false; }
                ticket.setStatus(TicketStatus.valueOf(action.status()));
                log.debug("Rule {} SET_STATUS {} on ticket {}", ruleId, action.status(), ticket.getId());
                yield true;
            }
            case "ASSIGN_GROUP" -> {
                if (action.groupId() == null) { log.warn("Rule {} ASSIGN_GROUP missing groupId", ruleId); yield false; }
                ticket.setAssignedGroupId(action.groupId());
                log.debug("Rule {} ASSIGN_GROUP {} on ticket {}", ruleId, action.groupId(), ticket.getId());
                yield true;
            }
            case "ASSIGN_USER" -> {
                if (action.userId() == null) { log.warn("Rule {} ASSIGN_USER missing userId", ruleId); yield false; }
                ticket.setAssignedUserId(action.userId());
                log.debug("Rule {} ASSIGN_USER {} on ticket {}", ruleId, action.userId(), ticket.getId());
                yield true;
            }
            default -> {
                log.warn("Rule {} unknown action type '{}' — skipping", ruleId, action.type());
                yield false;
            }
        };
    }
}
