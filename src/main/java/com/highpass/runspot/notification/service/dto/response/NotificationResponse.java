package com.highpass.runspot.notification.service.dto.response;

import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String body,
        NotificationActorResponse actor,
        Long sessionId,
        Long participationId,
        NotificationActionType actionType,
        NotificationActionStatus actionStatus,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
}
