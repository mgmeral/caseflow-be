package com.caseflow.customer.api.mapper;

import com.caseflow.customer.api.dto.CustomerEmailSettingsRequest;
import com.caseflow.customer.api.dto.CustomerEmailSettingsResponse;
import com.caseflow.customer.api.dto.RoutingRuleRequest;
import com.caseflow.customer.api.dto.RoutingRuleResponse;
import com.caseflow.customer.domain.CustomerEmailRoutingRule;
import com.caseflow.customer.domain.CustomerEmailSettings;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CustomerEmailSettingsMapper {

    public CustomerEmailSettings toEntity(CustomerEmailSettingsRequest request) {
        CustomerEmailSettings settings = new CustomerEmailSettings();
        settings.setUnknownSenderPolicy(request.unknownSenderPolicy());
        settings.setMatchingStrategy(request.matchingStrategy());
        settings.setIsActive(request.isActive() != null ? request.isActive() : Boolean.TRUE);
        return settings;
    }

    public CustomerEmailSettingsResponse toResponse(CustomerEmailSettings settings,
                                                    List<CustomerEmailRoutingRule> rules) {
        return new CustomerEmailSettingsResponse(
                settings.getId(),
                settings.getCustomerId(),
                settings.getUnknownSenderPolicy(),
                settings.getMatchingStrategy(),
                settings.getIsActive(),
                settings.getUpdatedAt(),
                rules.stream().map(this::toRuleResponse).toList()
        );
    }

    public CustomerEmailRoutingRule toRuleEntity(RoutingRuleRequest request) {
        CustomerEmailRoutingRule rule = new CustomerEmailRoutingRule();
        rule.setSenderMatchType(request.senderMatchType());
        rule.setMatchValue(request.matchValue().toLowerCase().trim());
        rule.setPriority(request.priority() != null ? request.priority() : 100);
        rule.setIsActive(request.isActive() != null ? request.isActive() : Boolean.TRUE);
        return rule;
    }

    public RoutingRuleResponse toRuleResponse(CustomerEmailRoutingRule rule) {
        return new RoutingRuleResponse(
                rule.getId(),
                rule.getCustomerId(),
                rule.getSenderMatchType(),
                rule.getMatchValue(),
                rule.getPriority(),
                rule.getIsActive(),
                rule.getCreatedAt()
        );
    }
}
