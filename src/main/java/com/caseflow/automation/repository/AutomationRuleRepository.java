package com.caseflow.automation.repository;

import com.caseflow.automation.domain.AutomationRule;
import com.caseflow.automation.domain.AutomationTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {

    /** Returns all active rules for a given trigger type, ordered for deterministic execution. */
    List<AutomationRule> findByTriggerTypeAndIsActiveTrueOrderByExecutionOrderAsc(
            AutomationTriggerType triggerType);

    /** Returns all rules ordered for management display. */
    List<AutomationRule> findAllByOrderByTriggerTypeAscExecutionOrderAsc();
}
