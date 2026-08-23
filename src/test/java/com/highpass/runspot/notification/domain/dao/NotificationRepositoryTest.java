package com.highpass.runspot.notification.domain.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.support.MySqlContainerSupport;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class NotificationRepositoryTest extends MySqlContainerSupport {

    private static final Long RECIPIENT_USER_ID = 1L;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void fiveTypes와_모든_enum은_MySQL에_문자열로_저장된다() {
        List<NotificationType> types = List.of(NotificationType.values());

        types.forEach(type -> notificationRepository.saveAndFlush(notification(RECIPIENT_USER_ID, type, type.name() + ":1")));
        entityManager.clear();

        assertThat(notificationRepository.findByRecipientUserIdOrderByIdDesc(RECIPIENT_USER_ID))
                .extracting(Notification::getType)
                .containsExactlyInAnyOrderElementsOf(types);
        assertThat(jdbcTemplate.queryForList("SELECT type FROM notifications", String.class))
                .containsExactlyInAnyOrderElementsOf(types.stream().map(NotificationType::name).toList());
        assertThat(jdbcTemplate.queryForList(
                        "SELECT action_status FROM notifications WHERE type = 'PARTICIPATION_REQUESTED'",
                        String.class))
                .containsExactly(NotificationActionStatus.PENDING.name());
        assertThat(jdbcTemplate.queryForList(
                        "SELECT action_type FROM notifications WHERE type = 'PARTICIPATION_REQUESTED'",
                        String.class))
                .containsExactly(NotificationActionType.APPROVE_OR_REJECT.name());
    }

    @Test
    void snapshotColumn은_readState변경_후에도_MySQL에서_변하지_않는다() throws NoSuchFieldException, IllegalAccessException {
        Notification saved = notificationRepository.saveAndFlush(
                notification(RECIPIENT_USER_ID, NotificationType.PARTICIPATION_APPROVED, "PARTICIPATION_APPROVED:200:1"));
        entityManager.clear();
        Notification persisted = notificationRepository.findById(saved.getId()).orElseThrow();
        Field title = Notification.class.getDeclaredField("title");
        title.setAccessible(true);
        title.set(persisted, "변조된 제목");

        persisted.markRead(LocalDateTime.of(2026, 8, 23, 9, 30));
        notificationRepository.saveAndFlush(persisted);
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("알림 제목");
        assertThat(Notification.class.getDeclaredField("title").getAnnotation(Column.class).updatable()).isFalse();
        assertThat(Notification.class.getDeclaredField("body").getAnnotation(Column.class).updatable()).isFalse();
        assertThat(Notification.class.getDeclaredField("actorName").getAnnotation(Column.class).updatable()).isFalse();
        assertThat(Notification.class.getDeclaredField("actorProfileImageUrl").getAnnotation(Column.class).updatable()).isFalse();
    }

    @Test
    void readAt이_null일때만_unread이며_반복읽기는_최초시각을_보존한다() {
        Notification saved = notificationRepository.saveAndFlush(
                notification(RECIPIENT_USER_ID, NotificationType.PARTICIPANT_KICKED, "PARTICIPANT_KICKED:200:1"));
        LocalDateTime firstReadAt = LocalDateTime.of(2026, 8, 23, 9, 30);

        saved.markRead(firstReadAt);
        saved.markRead(firstReadAt.plusMinutes(1));
        notificationRepository.saveAndFlush(saved);
        entityManager.clear();

        Notification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReadAt()).isEqualTo(firstReadAt);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE recipient_user_id = ? AND read_at IS NULL",
                        Integer.class,
                        RECIPIENT_USER_ID))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'notifications'",
                        String.class))
                .doesNotContain("read", "is_read");
    }

    @Test
    void recipientFeed는_id_내림차순이며_필수인덱스가_존재한다() {
        Notification oldest = notificationRepository.saveAndFlush(
                notification(RECIPIENT_USER_ID, NotificationType.PARTICIPATION_APPROVED, "PARTICIPATION_APPROVED:1:1"));
        Notification middle = notificationRepository.saveAndFlush(
                notification(RECIPIENT_USER_ID, NotificationType.PARTICIPATION_REJECTED, "PARTICIPATION_REJECTED:2:1"));
        Notification newest = notificationRepository.saveAndFlush(
                notification(RECIPIENT_USER_ID, NotificationType.PARTICIPANT_KICKED, "PARTICIPANT_KICKED:3:1"));
        notificationRepository.saveAndFlush(notification(2L, NotificationType.SESSION_START_REMINDER, "SESSION_START_REMINDER:4:2"));
        entityManager.clear();

        List<Notification> feed = notificationRepository.findByRecipientUserIdOrderByIdDesc(RECIPIENT_USER_ID);

        assertThat(feed).extracting(Notification::getId).containsExactly(newest.getId(), middle.getId(), oldest.getId());
        assertIndexColumns("idx_notifications_recipient_user_id_id", "recipient_user_id", "id");
        assertIndexColumns("idx_notifications_recipient_user_id_read_at", "recipient_user_id", "read_at");
    }

    @Test
    void duplicateDeduplicationKey는_DB에서_거부되고_한행만_남는다() {
        String deduplicationKey = "PARTICIPATION_REQUESTED:200:1";
        assertUniqueIndex("uk_notifications_deduplication_key", "deduplication_key");
        notificationRepository.saveAndFlush(notification(RECIPIENT_USER_ID, NotificationType.PARTICIPATION_REQUESTED, deduplicationKey));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(
                        notification(RECIPIENT_USER_ID, NotificationType.PARTICIPATION_REQUESTED, deduplicationKey)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE deduplication_key = ?",
                        Integer.class,
                        deduplicationKey))
                .isEqualTo(1);
    }

    private void assertUniqueIndex(String indexName, String columnName) {
        assertIndexColumns(indexName, columnName);
        Integer nonUnique = jdbcTemplate.queryForObject(
                "SELECT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = ?",
                Integer.class,
                indexName);

        assertThat(nonUnique).isZero();
    }

    private void assertIndexColumns(String indexName, String... columns) {
        List<Map<String, Object>> indexRows = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'notifications' AND index_name = ? ORDER BY seq_in_index",
                indexName);

        assertThat(indexRows)
                .extracting(row -> row.get("column_name"))
                .containsExactly((Object[]) columns);
    }

    private Notification notification(Long recipientUserId, NotificationType type, String deduplicationKey) {
        return Notification.builder()
                .recipientUserId(recipientUserId)
                .actorUserId(actorUserId(type))
                .actorName(actorName(type))
                .actorProfileImageUrl(actorProfileImageUrl(type))
                .type(type)
                .title("알림 제목")
                .body("알림 본문")
                .sessionId(100L)
                .participationId(participationId(type))
                .actionType(actionType(type))
                .actionStatus(actionStatus(type))
                .deduplicationKey(deduplicationKey)
                .build();
    }

    private Long actorUserId(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED, PARTICIPATION_APPROVED, PARTICIPATION_REJECTED, PARTICIPANT_KICKED -> 2L;
            case SESSION_START_REMINDER -> null;
        };
    }

    private String actorName(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED, PARTICIPATION_APPROVED, PARTICIPATION_REJECTED, PARTICIPANT_KICKED -> "러너";
            case SESSION_START_REMINDER -> null;
        };
    }

    private String actorProfileImageUrl(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED, PARTICIPATION_APPROVED, PARTICIPATION_REJECTED, PARTICIPANT_KICKED -> "https://cdn.runspot.test/runner.png";
            case SESSION_START_REMINDER -> null;
        };
    }

    private Long participationId(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED, PARTICIPATION_APPROVED, PARTICIPATION_REJECTED, PARTICIPANT_KICKED -> 200L;
            case SESSION_START_REMINDER -> null;
        };
    }

    private NotificationActionType actionType(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED -> NotificationActionType.APPROVE_OR_REJECT;
            case PARTICIPATION_APPROVED, PARTICIPATION_REJECTED, PARTICIPANT_KICKED, SESSION_START_REMINDER -> NotificationActionType.NAVIGATE;
        };
    }

    private NotificationActionStatus actionStatus(NotificationType type) {
        return switch (type) {
            case PARTICIPATION_REQUESTED -> NotificationActionStatus.PENDING;
            case PARTICIPATION_APPROVED, PARTICIPATION_REJECTED, PARTICIPANT_KICKED, SESSION_START_REMINDER -> NotificationActionStatus.NONE;
        };
    }
}
