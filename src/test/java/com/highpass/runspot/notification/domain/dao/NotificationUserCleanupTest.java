package com.highpass.runspot.notification.domain.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.service.AuthService;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest
class NotificationUserCleanupTest extends MySqlContainerSupport {

    private static final String DEDUPLICATION_PREFIX = "notification-user-cleanup:";
    private static final Long TARGET_USER_ID = 90001L;
    private static final Long OTHER_USER_ID = 90002L;

    @Autowired
    private AuthService authService;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void cleanTaskData() {
        jdbcTemplate.update(
                "DELETE FROM notifications WHERE deduplication_key LIKE ?",
                DEDUPLICATION_PREFIX + "%"
        );
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE ?", "notification-user-cleanup-%");
    }

    @Test
    void recipientActorAndBothNotificationsAreDeletedWhileUnrelatedNotificationRemains() {
        Notification recipientOnly = notification(TARGET_USER_ID, OTHER_USER_ID, "recipient-only");
        Notification actorOnly = notification(OTHER_USER_ID, TARGET_USER_ID, "actor-only");
        Notification both = notification(TARGET_USER_ID, TARGET_USER_ID, "both");
        Notification unrelated = notification(OTHER_USER_ID, OTHER_USER_ID, "unrelated");
        notificationRepository.saveAllAndFlush(List.of(recipientOnly, actorOnly, both, unrelated));

        int deletedCount = transactionTemplate.execute(
                status -> notificationRepository.deleteAllRelatedToUser(TARGET_USER_ID)
        );
        entityManager.clear();

        List<Long> remainingIds = jdbcTemplate.queryForList(
                "SELECT id FROM notifications WHERE deduplication_key LIKE ? ORDER BY id",
                Long.class,
                DEDUPLICATION_PREFIX + "%"
        );

        assertThat(deletedCount).isEqualTo(3);
        assertThat(remainingIds).containsExactly(unrelated.getId());
        assertThat(notificationRepository.findById(recipientOnly.getId())).isEmpty();
        assertThat(notificationRepository.findById(actorOnly.getId())).isEmpty();
        assertThat(notificationRepository.findById(both.getId())).isEmpty();
    }

    @Test
    void withdrawRollsBackNotificationCleanupWhenUserDeletionFails() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("notification-user-cleanup-rollback")
                .password("password")
                .name("롤백 사용자")
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build());
        Notification notification = notification(user.getId(), OTHER_USER_ID, "rollback");
        notificationRepository.saveAndFlush(notification);
        doThrow(new IllegalStateException("force withdraw rollback"))
                .when(userRepository)
                .deleteById(user.getId());

        assertThatThrownBy(() -> authService.withdraw(user.getId()))
                .isInstanceOf(RuntimeException.class);
        entityManager.clear();

        assertThat(notificationRepository.findById(notification.getId())).isPresent();
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    private Notification notification(Long recipientUserId, Long actorUserId, String suffix) {
        return Notification.builder()
                .recipientUserId(recipientUserId)
                .actorUserId(actorUserId)
                .actorName("알림 행위자")
                .actorProfileImageUrl("https://cdn.runspot.test/actor.png")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("알림 제목")
                .body("알림 본문")
                .sessionId(1L)
                .participationId(1L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey(DEDUPLICATION_PREFIX + suffix)
                .build();
    }
}
