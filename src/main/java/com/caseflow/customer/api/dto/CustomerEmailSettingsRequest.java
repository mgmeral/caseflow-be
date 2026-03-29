package com.caseflow.customer.api.dto;

import com.caseflow.common.domain.UnknownSenderPolicy;
import jakarta.validation.constraints.NotNull;

public record CustomerEmailSettingsRequest(

        @NotNull
        UnknownSenderPolicy unknownSenderPolicy,

        Boolean isActive,
        Boolean allowSubdomains,
        Long defaultGroupId,
        String defaultPriority
) {}
