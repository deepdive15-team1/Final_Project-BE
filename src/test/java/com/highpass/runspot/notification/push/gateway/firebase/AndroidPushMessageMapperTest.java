package com.highpass.runspot.notification.push.gateway.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.Message;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class AndroidPushMessageMapperTest {

    private static final String DEVICE_TOKEN = "test-device-token";
    private final AndroidPushMessageMapper mapper = new AndroidPushMessageMapper();

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void mapsEveryNotificationTypeToThePersistedAndroidPayload(NotificationType type) {
        Message message = mapper.map(pushMessage(type, 19L));

        Map<String, String> data = field(message, "data");
        assertThat(data).containsExactlyInAnyOrderEntriesOf(Map.of(
                "notificationId", "41",
                "type", type.name(),
                "sessionId", "7",
                "participationId", "19",
                "actionType", "APPROVE_OR_REJECT",
                "actionStatus", "PENDING"
        ));
        assertThat(data.values()).doesNotContain("null");
        assertThat(data).doesNotContainKeys(
                "actorUserId", "actorName", "actorProfileImageUrl", "messageToHost", "token", "credential", "privateKey"
        );
        String token = field(message, "token");
        Object fid = field(message, "fid");
        Object topic = field(message, "topic");
        Object condition = field(message, "condition");
        Object apnsConfig = field(message, "apnsConfig");
        assertThat(token).isEqualTo(DEVICE_TOKEN);
        assertThat(fid).isNull();
        assertThat(topic).isNull();
        assertThat(condition).isNull();
        assertThat(apnsConfig).isNull();

        Object notification = field(message, "notification");
        String title = field(notification, "title");
        String body = field(notification, "body");
        assertThat(title).isEqualTo("Persisted title");
        assertThat(body).isEqualTo("Persisted body");

        AndroidConfig androidConfig = field(message, "androidConfig");
        String priority = field(androidConfig, "priority");
        assertThat(priority).isEqualTo("high");
        AndroidNotification androidNotification = field(androidConfig, "notification");
        String channelId = field(androidNotification, "channelId");
        String clickAction = field(androidNotification, "clickAction");
        assertThat(channelId).isEqualTo("runspot_notifications");
        assertThat(clickAction).isEqualTo("RUNSPOT_NOTIFICATION_CLICK");
    }

    @Test
    void omitsNullParticipationIdInsteadOfSendingANullString() {
        Message message = mapper.map(pushMessage(NotificationType.SESSION_START_REMINDER, null));

        Map<String, String> data = field(message, "data");
        assertThat(data).doesNotContainKey("participationId");
        assertThat(data.values()).doesNotContain("null");
    }

    private PushMessage pushMessage(NotificationType type, Long participationId) {
        return PushMessage.from(Notification.builder()
                .id(41L)
                .recipientUserId(11L)
                .actorUserId(12L)
                .actorName("Actor name")
                .actorProfileImageUrl("https://example.com/profile.png")
                .type(type)
                .title("Persisted title")
                .body("Persisted body")
                .sessionId(7L)
                .participationId(participationId)
                .actionType(NotificationActionType.APPROVE_OR_REJECT)
                .actionStatus(NotificationActionStatus.PENDING)
                .deduplicationKey("notification-41")
                .build(), DEVICE_TOKEN);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
