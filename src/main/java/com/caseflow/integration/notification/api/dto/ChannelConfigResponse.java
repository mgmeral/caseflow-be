package com.caseflow.integration.notification.api.dto;

import com.caseflow.integration.notification.domain.NotificationChannelConfig;

import java.time.Instant;

public record ChannelConfigResponse(
        Long id,
        String name,
        String channelType,
        boolean enabled,
        /** Always "****" — never expose the real webhook URL. */
        String webhookUrl,
        String subscribedEvents,
        String scopeType,
        Long scopeId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChannelConfigResponse from(NotificationChannelConfig c) {
        return new ChannelConfigResponse(
                c.getId(),
                c.getName(),
                c.getChannelType().name(),
                Boolean.TRUE.equals(c.getIsEnabled()),
                "****",
                c.getSubscribedEvents(),
                c.getScopeType(),
                c.getScopeId(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
