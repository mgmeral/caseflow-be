package com.caseflow.customer.api.dto;

import com.caseflow.common.domain.UnknownSenderPolicy;
import com.caseflow.customer.domain.MatchingStrategy;
import jakarta.validation.constraints.NotNull;

public record CustomerEmailSettingsRequest(

        @NotNull
        UnknownSenderPolicy unknownSenderPolicy,

        @NotNull
        MatchingStrategy matchingStrategy,

        Boolean isActive
) {}
