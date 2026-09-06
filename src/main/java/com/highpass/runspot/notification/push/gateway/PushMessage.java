package com.highpass.runspot.notification.push.gateway;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import java.util.Objects;

public record PushMessage(
        long notificationId,
        String token,
        NotificationType type,
        String title,
        String body,
        long sessionId,
        Long participationId,
        NotificationActionType actionType,
        NotificationActionStatus actionStatus
) {

    public PushMessage {
        if (notificationId <= 0 || sessionId <= 0) {
            throw new IllegalArgumentException("알림 및 세션 ID는 양수여야 합니다.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("FCM 토큰은 비어 있을 수 없습니다.");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(actionType, "actionType must not be null");
        Objects.requireNonNull(actionStatus, "actionStatus must not be null");
    }

    public static PushMessage from(Notification notification, String token) {
        Objects.requireNonNull(notification, "notification must not be null");
        return new PushMessage(
                notification.getId(),
                token,
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getSessionId(),
                notification.getParticipationId(),
                notification.getActionType(),
                notification.getActionStatus()
        );
    }

    @Override
    public String toString() {
        return "PushMessage[notificationId=" + notificationId + ", token=[redacted], type=" + type + "]";
    }
}
