package com.highpass.runspot.notification.push.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
class PushOutboxRepositoryTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);

    @Autowired
    private PushOutboxRepository pushOutboxRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void schemaHasUniqueLazyNotificationAndClaimIndex() {
        Notification notification = notificationRepository.saveAndFlush(notification("schema"));
        PushOutbox saved = pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, NOW));
        entityManager.clear();

        PushOutbox reloaded = pushOutboxRepository.findById(saved.getId()).orElseThrow();

        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(reloaded, "notification")).isFalse();
        assertThat(reloaded.getRecipientUserId()).isEqualTo(notification.getRecipientUserId());
        assertIndexColumns("idx_push_outbox_claim", "status", "next_attempt_at", "id");
        assertUniqueIndex("uk_push_outbox_notification_id", "notification_id");
        assertRequiredColumns(
                "notification_id",
                "recipient_user_id",
                "status",
                "attempts",
                "next_attempt_at"
        );
        assertThat(jdbcTemplate.queryForList(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = DATABASE() AND table_name = 'push_outbox'",
                        String.class))
                .contains("lease_until", "lease_token", "sent_at", "terminal_at", "last_error_code");
    }

    @Test
    void duplicateNotificationIsRejectedByDatabase() {
        Notification notification = notificationRepository.saveAndFlush(notification("duplicate"));
        pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, NOW));

        assertThatThrownBy(() -> pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(pushOutboxRepository.count()).isEqualTo(1L);
    }

    private void assertRequiredColumns(String... columns) {
        List<String> nonNullableColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_outbox' AND is_nullable = 'NO'",
                String.class
        );
        assertThat(nonNullableColumns).contains(columns);
    }

    private void assertUniqueIndex(String indexName, String columnName) {
        assertIndexColumns(indexName, columnName);
        Integer nonUnique = jdbcTemplate.queryForObject(
                "SELECT non_unique FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_outbox' AND index_name = ?",
                Integer.class,
                indexName
        );
        assertThat(nonUnique).isZero();
    }

    private void assertIndexColumns(String indexName, String... columns) {
        List<Map<String, Object>> indexRows = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_outbox' "
                        + "AND index_name = ? ORDER BY seq_in_index",
                indexName
        );
        assertThat(indexRows)
                .extracting(row -> row.get("column_name"))
                .containsExactly((Object[]) columns);
    }

    private Notification notification(String suffix) {
        return Notification.builder()
                .recipientUserId(1001L)
                .actorUserId(1002L)
                .actorName("아웃박스 행위자")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("아웃박스 알림")
                .body("아웃박스 본문")
                .sessionId(2001L)
                .participationId(3001L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey("push-outbox-repository:" + suffix)
                .build();
    }
}
