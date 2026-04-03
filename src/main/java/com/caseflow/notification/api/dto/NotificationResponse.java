package com.caseflow.notification.api.dto;

import com.caseflow.notification.domain.NotificationType;
import com.caseflow.notification.domain.UserNotification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        Long id,
        Long userId,
        NotificationType type,
        String title,
        String message,
        Long ticketId,
        UUID ticketPublicId,
        String ticketNo,
        Long groupId,
        Long actorUserId,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(UserNotification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getTicketId(),
                n.getTicketPublicId(),
                n.getTicketNo(),
                n.getGroupId(),
                n.getActorUserId(),
                n.getIsRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
