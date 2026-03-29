package com.caseflow.common.exception;

public class RoutingRuleNotFoundException extends RuntimeException {

    public RoutingRuleNotFoundException(Long ruleId) {
        super("Routing rule not found: " + ruleId);
    }

    public RoutingRuleNotFoundException(Long ruleId, Long customerId) {
        super("Routing rule " + ruleId + " not found for customer " + customerId);
    }
}
