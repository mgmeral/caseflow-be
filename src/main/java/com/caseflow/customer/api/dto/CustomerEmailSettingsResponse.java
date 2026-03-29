package com.caseflow.customer.api.dto;

import com.caseflow.common.domain.UnknownSenderPolicy;

import java.time.Instant;
import java.util.List;

public record CustomerEmailSettingsResponse(
        Long id,
        Long customerId,
        UnknownSenderPolicy unknownSenderPolicy,
        Boolean isActive,
        Boolean allowSubdomains,
        Long defaultGroupId,
        String defaultPriority,
        Instant updatedAt,
        List<RoutingRuleResponse> rules
) {}
