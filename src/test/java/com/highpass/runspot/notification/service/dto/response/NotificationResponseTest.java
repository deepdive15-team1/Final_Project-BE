package com.highpass.runspot.notification.service.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.highpass.runspot.common.exception.BaseException;
import com.highpass.runspot.common.exception.dto.ErrorResponse;
import com.highpass.runspot.common.exception.handler.ApiExceptionHandler;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.exception.NotificationErrorCode;
import com.highpass.runspot.notification.exception.NotificationException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class NotificationResponseTest {

    private static final Set<String> NOTIFICATION_FIELDS = Set.of(
            "id",
            "type",
            "title",
            "body",
            "actor",
            "sessionId",
            "participationId",
            "actionType",
            "actionStatus",
            "read",
            "readAt",
            "createdAt"
    );

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void requestNotificationSerializesTheTypedContractWithoutPersistenceOrPageFields() throws Exception {
        NotificationResponse response = new NotificationResponse(
                101L,
                NotificationType.PARTICIPATION_REQUESTED,
                "새로운 러너가 대기 중이에요!",
                "러너님이 [한강 러닝]에 참여를 신청했습니다.",
                new NotificationActorResponse(202L, "러너", null),
                303L,
                404L,
                NotificationActionType.APPROVE_OR_REJECT,
                NotificationActionStatus.PENDING,
                false,
                null,
                LocalDateTime.of(2026, 8, 23, 9, 0)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(NOTIFICATION_FIELDS);
        assertThat(json.get("id").asLong()).isEqualTo(101L);
        assertThat(json.get("type").asText()).isEqualTo("PARTICIPATION_REQUESTED");
        assertThat(json.get("title").asText()).isEqualTo("새로운 러너가 대기 중이에요!");
        assertThat(json.get("body").asText()).isEqualTo("러너님이 [한강 러닝]에 참여를 신청했습니다.");
        assertThat(json.get("sessionId").asLong()).isEqualTo(303L);
        assertThat(json.get("participationId").asLong()).isEqualTo(404L);
        assertThat(json.get("actionType").asText()).isEqualTo("APPROVE_OR_REJECT");
        assertThat(json.get("actionStatus").asText()).isEqualTo("PENDING");
        assertThat(json.get("read").isBoolean()).isTrue();
        assertThat(json.get("read").asBoolean()).isFalse();
        assertThat(json.get("readAt").isNull()).isTrue();
        assertThat(json.get("actor").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "name", "profileImageUrl");
        assertThat(json.get("actor").get("id").asLong()).isEqualTo(202L);
        assertThat(json.get("actor").get("name").asText()).isEqualTo("러너");
        assertThat(json.get("actor").get("profileImageUrl").isNull()).isTrue();
        assertThat(json.has("entity")).isFalse();
        assertThat(json.has("pageable")).isFalse();
        assertThat(json.has("sort")).isFalse();
    }

    @Test
    void reminderNotificationSerializesNullableActorAndContextAsNull() throws Exception {
        NotificationResponse response = new NotificationResponse(
                105L,
                NotificationType.SESSION_START_REMINDER,
                "러닝 시작 30분 전",
                "스트레칭을 잊지 마세요! [한강 러닝] 러닝이 곧 시작됩니다.",
                null,
                303L,
                null,
                NotificationActionType.NAVIGATE,
                NotificationActionStatus.NONE,
                false,
                null,
                LocalDateTime.of(2026, 8, 23, 9, 0)
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(fieldNames(json)).containsExactlyInAnyOrderElementsOf(NOTIFICATION_FIELDS);
        assertThat(json.get("type").asText()).isEqualTo("SESSION_START_REMINDER");
        assertThat(json.get("actor").isNull()).isTrue();
        assertThat(json.get("participationId").isNull()).isTrue();
        assertThat(json.get("readAt").isNull()).isTrue();
        assertThat(json.get("actionType").asText()).isEqualTo("NAVIGATE");
        assertThat(json.get("actionStatus").asText()).isEqualTo("NONE");
        assertThat(json.get("read").asBoolean()).isFalse();
    }

    @Test
    void feedAndUnreadCountSerializeOnlyTheirPublicFields() throws Exception {
        NotificationResponse notification = new NotificationResponse(
                101L,
                NotificationType.PARTICIPATION_APPROVED,
                "참여가 확정되었습니다!",
                "[한강 러닝] 참여 신청이 승인되었습니다.",
                null,
                303L,
                404L,
                NotificationActionType.NAVIGATE,
                NotificationActionStatus.NONE,
                true,
                LocalDateTime.of(2026, 8, 23, 9, 1),
                LocalDateTime.of(2026, 8, 23, 9, 0)
        );

        JsonNode feedJson = objectMapper.readTree(objectMapper.writeValueAsString(
                new NotificationFeedResponse(List.of(notification), 101L, true)
        ));
        JsonNode unreadJson = objectMapper.readTree(objectMapper.writeValueAsString(
                new NotificationUnreadCountResponse(2L)
        ));

        assertThat(fieldNames(feedJson)).containsExactlyInAnyOrder("notifications", "nextCursorId", "hasNext");
        assertThat(feedJson.get("notifications").isArray()).isTrue();
        assertThat(feedJson.get("notifications").size()).isEqualTo(1);
        assertThat(feedJson.get("nextCursorId").asLong()).isEqualTo(101L);
        assertThat(feedJson.get("hasNext").asBoolean()).isTrue();
        assertThat(feedJson.has("content")).isFalse();
        assertThat(feedJson.has("pageable")).isFalse();
        assertThat(feedJson.has("sort")).isFalse();
        assertThat(fieldNames(unreadJson)).containsExactly("unreadCount");
        assertThat(unreadJson.get("unreadCount").asLong()).isEqualTo(2L);
    }

    @Test
    void notificationNotFoundUsesTheCommonBaseExceptionHandler404Contract() throws Exception {
        NotificationException exception = new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleBaseException(exception);

        assertThat(exception).isInstanceOf(BaseException.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("알림을 찾을 수 없습니다.");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response.getBody()));
        assertThat(fieldNames(json)).containsExactlyInAnyOrder("status", "message");
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("message").asText()).isEqualTo("알림을 찾을 수 없습니다.");
    }

    private Set<String> fieldNames(JsonNode json) {
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
