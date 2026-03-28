package com.caseflow.customer.api.dto;

import com.caseflow.common.domain.UnknownSenderPolicy;
import com.caseflow.customer.domain.MatchingStrategy;

import java.time.Instant;
import java.util.List;

public record CustomerEmailSettingsResponse(
        Long id,
        Long customerId,
        UnknownSenderPolicy unknownSenderPolicy,
        MatchingStrategy matchingStrategy,
        Boolean isActive,
        Instant updatedAt,
        List<RoutingRuleResponse> rules
) {}
