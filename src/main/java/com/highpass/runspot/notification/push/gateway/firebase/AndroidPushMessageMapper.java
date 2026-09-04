package com.highpass.runspot.notification.push.gateway.firebase;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class AndroidPushMessageMapper {

    private static final String CHANNEL_ID = "runspot_notifications";
    private static final String CLICK_ACTION = "RUNSPOT_NOTIFICATION_CLICK";

    Message map(PushMessage pushMessage) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", String.valueOf(pushMessage.notificationId()));
        data.put("type", notificationType(pushMessage.type()));
        data.put("sessionId", String.valueOf(pushMessage.sessionId()));
        if (pushMessage.participationId() != null) {
            data.put("participationId", String.valueOf(pushMessage.participationId()));
        }
        data.put("actionType", pushMessage.actionType().name());
        data.put("actionStatus", pushMessage.actionStatus().name());

        return Message.builder()
                .setToken(pushMessage.token())
                .setNotification(Notification.builder()
                        .setTitle(pushMessage.title())
                        .setBody(pushMessage.body())
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(CHANNEL_ID)
                                .setClickAction(CLICK_ACTION)
                                .build())
                        .build())
                .putAllData(data)
                .build();
    }

    private String notificationType(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED -> "PARTICIPATION_REQUESTED";
            case PARTICIPATION_APPROVED -> "PARTICIPATION_APPROVED";
            case PARTICIPATION_REJECTED -> "PARTICIPATION_REJECTED";
            case PARTICIPANT_KICKED -> "PARTICIPANT_KICKED";
            case SESSION_START_REMINDER -> "SESSION_START_REMINDER";
        };
    }
}
