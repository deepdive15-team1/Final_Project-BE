package com.highpass.runspot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.service.dto.response.NotificationActorResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationFeedResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationResponse;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class NotificationQueryServiceTest extends MySqlContainerSupport {

    private static final long RECIPIENT_USER_ID = 10L;
    private static final long FOREIGN_RECIPIENT_USER_ID = 20L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 24, 9, 0);
    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 8, 24, 9, 5);

    @Autowired
    private NotificationQueryService notificationQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM notifications");
        insertNotification(101L, RECIPIENT_USER_ID, 701L, "첫 번째 러너", "https://cdn.runspot.test/first.png", null);
        insertNotification(102L, RECIPIENT_USER_ID, null, null, null, READ_AT);
        insertNotification(103L, RECIPIENT_USER_ID, 901L, "신규 러너", null, null);
        insertNotification(104L, FOREIGN_RECIPIENT_USER_ID, 999L, "다른 수신자의 러너", null, null);
    }

    @Test
    void recipientCursorFeed는_수신자를_격리하고_내림차순으로_두페이지를_반환한다() {
        NotificationFeedResponse firstPage = notificationQueryService.getNotificationFeed(RECIPIENT_USER_ID, null, 2);
        NotificationFeedResponse secondPage = notificationQueryService.getNotificationFeed(
                RECIPIENT_USER_ID,
                firstPage.nextCursorId(),
                2
        );

        assertThat(firstPage.notifications()).extracting(NotificationResponse::id).containsExactly(103L, 102L);
        assertThat(firstPage.notifications()).extracting(NotificationResponse::id).doesNotContain(104L);
        assertThat(firstPage.nextCursorId()).isEqualTo(102L);
        assertThat(firstPage.hasNext()).isTrue();

        NotificationResponse newest = firstPage.notifications().get(0);
        assertThat(newest.actor()).isEqualTo(new NotificationActorResponse(901L, "신규 러너", null));
        assertThat(newest.read()).isFalse();
        assertThat(newest.readAt()).isNull();
        assertThat(newest.createdAt()).isEqualTo(CREATED_AT.plusMinutes(3));

        NotificationResponse middle = firstPage.notifications().get(1);
        assertThat(middle.actor()).isNull();
        assertThat(middle.read()).isTrue();
        assertThat(middle.readAt()).isEqualTo(READ_AT);
        assertThat(middle.createdAt()).isEqualTo(CREATED_AT.plusMinutes(2));

        assertThat(secondPage.notifications()).extracting(NotificationResponse::id).containsExactly(101L);
        assertThat(secondPage.notifications()).extracting(NotificationResponse::id).doesNotContain(104L);
        assertThat(secondPage.nextCursorId()).isEqualTo(101L);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void unreadCount는_수신자별_readAtNull행만_계산한다() {
        assertThat(notificationQueryService.getUnreadCount(RECIPIENT_USER_ID).unreadCount()).isEqualTo(2L);
    }

    @Test
    void cursor이후_행이없으면_빈피드와_null커서를_반환한다() {
        NotificationFeedResponse response = notificationQueryService.getNotificationFeed(RECIPIENT_USER_ID, 100L, 100);

        assertThat(response.notifications()).isEmpty();
        assertThat(response.nextCursorId()).isNull();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void invalidSize와_양수가아닌Cursor는_IllegalArgumentException이다() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> notificationQueryService.getNotificationFeed(RECIPIENT_USER_ID, null, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> notificationQueryService.getNotificationFeed(RECIPIENT_USER_ID, null, 101));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> notificationQueryService.getNotificationFeed(RECIPIENT_USER_ID, 0L, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> notificationQueryService.getNotificationFeed(RECIPIENT_USER_ID, -1L, 1));
    }

    private void insertNotification(
            long id,
            long recipientUserId,
            Long actorUserId,
            String actorName,
            String actorProfileImageUrl,
            LocalDateTime readAt
    ) {
        LocalDateTime createdAt = CREATED_AT.plusMinutes(id - 100L);
        jdbcTemplate.update(
                "INSERT INTO notifications (id, recipient_user_id, actor_user_id, actor_name, actor_profile_image_url, "
                        + "type, title, body, session_id, participation_id, action_type, action_status, read_at, "
                        + "deduplication_key, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                recipientUserId,
                actorUserId,
                actorName,
                actorProfileImageUrl,
                NotificationType.PARTICIPATION_APPROVED.name(),
                "알림 제목 " + id,
                "알림 본문 " + id,
                300L,
                400L,
                NotificationActionType.NAVIGATE.name(),
                NotificationActionStatus.NONE.name(),
                readAt,
                "notification-query-" + id,
                createdAt,
                createdAt
        );
    }
}
