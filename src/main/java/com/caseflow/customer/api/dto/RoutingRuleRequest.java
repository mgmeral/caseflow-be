package com.caseflow.customer.api.dto;

import com.caseflow.customer.domain.SenderMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoutingRuleRequest(

        @NotNull
        SenderMatchType senderMatchType,

        @NotBlank
        String matchValue,

        Integer priority,

        Boolean isActive,

        /** Only meaningful for DOMAIN rules — also matches sub-domains when true. */
        Boolean allowSubdomains
) {}
