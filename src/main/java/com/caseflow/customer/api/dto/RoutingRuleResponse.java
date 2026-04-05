package com.caseflow.customer.api.dto;

import com.caseflow.customer.domain.SenderMatchType;

import java.time.Instant;

public record RoutingRuleResponse(
        Long id,
        Long customerId,
        SenderMatchType senderMatchType,
        String matchValue,
        Integer priority,
        Boolean isActive,
        /** Only meaningful for DOMAIN rules — true when sub-domains are also matched. */
        Boolean allowSubdomains,
        Instant createdAt,
        Instant updatedAt
) {}
