package com.highpass.runspot.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void markRead는_처음_전달된_시각만_저장한다() {
        Notification notification = pendingNotification();
        LocalDateTime firstReadAt = LocalDateTime.of(2026, 8, 23, 9, 30);
        LocalDateTime repeatedReadAt = firstReadAt.plusMinutes(1);

        notification.markRead(firstReadAt);
        notification.markRead(repeatedReadAt);

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void resolveAction은_PENDING_액션을_한번만_RESOLVED로_전이시킨다() {
        Notification notification = pendingNotification();

        notification.resolveAction();
        notification.resolveAction();

        assertThat(notification.getActionStatus()).isEqualTo(NotificationActionStatus.RESOLVED);
    }

    @Test
    void resolveAction은_NONE_액션의_상태를_변경하지_않는다() {
        Notification notification = Notification.builder()
                .recipientUserId(1L)
                .type(NotificationType.SESSION_START_REMINDER)
                .title("러닝 시작 30분 전")
                .body("스트레칭을 잊지 마세요!")
                .sessionId(100L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey("SESSION_START_REMINDER:100:1")
                .build();

        notification.resolveAction();

        assertThat(notification.getActionStatus()).isEqualTo(NotificationActionStatus.NONE);
    }

    private Notification pendingNotification() {
        return Notification.builder()
                .recipientUserId(1L)
                .actorUserId(2L)
                .actorName("러너")
                .actorProfileImageUrl("https://cdn.runspot.test/runner.png")
                .type(NotificationType.PARTICIPATION_REQUESTED)
                .title("새로운 러너가 대기 중이에요!")
                .body("러너님이 [한강 러닝]에 참여를 신청했습니다.")
                .sessionId(100L)
                .participationId(200L)
                .actionType(NotificationActionType.APPROVE_OR_REJECT)
                .actionStatus(NotificationActionStatus.PENDING)
                .deduplicationKey("PARTICIPATION_REQUESTED:200:1")
                .build();
    }
}
