package com.highpass.runspot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.exception.NotificationErrorCode;
import com.highpass.runspot.notification.exception.NotificationException;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import(NotificationCommandServiceTest.FixedClockConfiguration.class)
class NotificationCommandServiceTest extends MySqlContainerSupport {

    private static final long RECIPIENT_USER_ID = 10L;
    private static final long FOREIGN_RECIPIENT_USER_ID = 20L;
    private static final LocalDateTime FIXED_READ_AT = LocalDateTime.of(2026, 8, 24, 9, 5);
    private static final LocalDateTime PREVIOUS_READ_AT = LocalDateTime.of(2026, 8, 24, 9, 0);
    private static final String DEDUPLICATION_PREFIX = "notification-command-";

    @Autowired
    private NotificationCommandService notificationCommandService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanCommandNotifications() {
        jdbcTemplate.update("DELETE FROM notifications WHERE deduplication_key LIKE ?", DEDUPLICATION_PREFIX + "%");
    }

    @Test
    void 내_안읽은_알림은_한번만_읽음처리되고_반복호출도_성공한다() {
        Notification firstUnread = notification(RECIPIENT_USER_ID, "first-unread", null);
        notification(RECIPIENT_USER_ID, "second-unread", null);

        assertThat(unreadCount(RECIPIENT_USER_ID)).isEqualTo(2L);

        notificationCommandService.markAsRead(firstUnread.getId(), RECIPIENT_USER_ID);

        assertThat(unreadCount(RECIPIENT_USER_ID)).isEqualTo(1L);
        assertThat(readAt(firstUnread.getId())).isEqualTo(FIXED_READ_AT);

        notificationCommandService.markAsRead(firstUnread.getId(), RECIPIENT_USER_ID);

        assertThat(unreadCount(RECIPIENT_USER_ID)).isEqualTo(1L);
        assertThat(readAt(firstUnread.getId())).isEqualTo(FIXED_READ_AT);

        notificationCommandService.markAllAsRead(RECIPIENT_USER_ID);

        assertThat(unreadCount(RECIPIENT_USER_ID)).isZero();
    }

    @Test
    void 전체_읽음은_수신자의_안읽은_행에만_한번의_고정시각을_사용한다() {
        Notification firstUnread = notification(RECIPIENT_USER_ID, "first-unread", null);
        Notification secondUnread = notification(RECIPIENT_USER_ID, "second-unread", null);
        Notification alreadyRead = notification(RECIPIENT_USER_ID, "already-read", PREVIOUS_READ_AT);
        Notification foreignUnread = notification(FOREIGN_RECIPIENT_USER_ID, "foreign-unread", null);

        notificationCommandService.markAllAsRead(RECIPIENT_USER_ID);

        assertThat(readAt(firstUnread.getId())).isEqualTo(FIXED_READ_AT);
        assertThat(readAt(secondUnread.getId())).isEqualTo(FIXED_READ_AT);
        assertThat(readAt(alreadyRead.getId())).isEqualTo(PREVIOUS_READ_AT);
        assertThat(readAt(foreignUnread.getId())).isNull();
        assertThat(unreadCount(RECIPIENT_USER_ID)).isZero();
        assertThat(unreadCount(FOREIGN_RECIPIENT_USER_ID)).isEqualTo(1L);
    }

    @Test
    void 외국_또는_없는_알림은_동일한_notFound오류를_반환하고_변경하지_않는다() {
        Notification foreignUnread = notification(FOREIGN_RECIPIENT_USER_ID, "foreign-unread", null);

        NotificationException foreignException = assertThrows(
                NotificationException.class,
                () -> notificationCommandService.markAsRead(foreignUnread.getId(), RECIPIENT_USER_ID)
        );
        NotificationException absentException = assertThrows(
                NotificationException.class,
                () -> notificationCommandService.markAsRead(Long.MAX_VALUE, RECIPIENT_USER_ID)
        );

        assertThat(foreignException.getClass()).isEqualTo(absentException.getClass());
        assertThat(foreignException.getExceptionType()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(absentException.getExceptionType()).isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(readAt(foreignUnread.getId())).isNull();
        assertThat(unreadCount(FOREIGN_RECIPIENT_USER_ID)).isEqualTo(1L);
    }

    private Notification notification(long recipientUserId, String label, LocalDateTime readAt) {
        return notificationRepository.saveAndFlush(Notification.builder()
                .recipientUserId(recipientUserId)
                .actorUserId(100L)
                .actorName("테스트 러너")
                .actorProfileImageUrl("https://cdn.runspot.test/runner.png")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("알림 제목")
                .body("알림 본문")
                .sessionId(200L)
                .participationId(300L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .readAt(readAt)
                .deduplicationKey(DEDUPLICATION_PREFIX + label)
                .build());
    }

    private long unreadCount(long recipientUserId) {
        return notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    private LocalDateTime readAt(long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?",
                LocalDateTime.class,
                notificationId
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock commandClock() {
            return Clock.fixed(Instant.parse("2026-08-24T00:05:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
