package com.caseflow.integration.notification.api.dto;

import com.caseflow.integration.notification.domain.ChannelType;
import com.caseflow.integration.notification.domain.NotificationEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChannelConfigRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull ChannelType channelType,
        /** Omit or send blank to preserve the existing webhook URL on update. */
        @Size(max = 5000) String webhookUrl,
        @NotNull List<NotificationEventType> subscribedEvents,
        @Size(max = 50) String scopeType,
        Long scopeId,
        boolean enabled
) {}
