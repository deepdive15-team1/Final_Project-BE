package com.highpass.runspot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.config.FcmPushProperties;
import com.highpass.runspot.notification.push.domain.PushDeviceToken;
import com.highpass.runspot.notification.push.domain.PushPlatform;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.service.PushOutboxEnqueuer;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(properties = "push.fcm.enabled=true")
@Import(PushNotificationEnqueueIntegrationTest.DuplicateEnqueuerConfiguration.class)
class PushNotificationEnqueueIntegrationTest extends MySqlContainerSupport {

    private static final long HOST_ID = 101L;
    private static final long APPLICANT_ID = 102L;
    private static final long REMINDER_RECIPIENT_ID = 103L;
    private static final long SESSION_ID = 201L;
    private static final long PARTICIPATION_ID = 301L;

    @MockitoBean
    private FirebaseApp firebaseApp;
    @MockitoBean
    private FirebaseMessaging firebaseMessaging;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationReminderCreator notificationReminderCreator;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PushOutboxRepository pushOutboxRepository;
    @Autowired
    private PushDeviceTokenRepository pushDeviceTokenRepository;
    @Autowired
    private DuplicateOnDemandEnqueuer duplicateOnDemandEnqueuer;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    @AfterEach
    void cleanData() {
        duplicateOnDemandEnqueuer.clearDuplicateRequest();
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
        pushDeviceTokenRepository.deleteAll();
    }

    @Test
    void 현재_토큰이_있으면_다섯_알림_유형마다_정확히_하나의_아웃박스를_만든다() {
        registerToken(HOST_ID);
        registerToken(APPLICANT_ID);
        registerToken(REMINDER_RECIPIENT_ID);
        SessionParticipant participation = participation();

        notificationService.notifyParticipationRequested(participation);
        notificationService.notifyParticipationApproved(participation);
        notificationService.notifyParticipationRejected(participation);
        notificationService.notifyParticipantKicked(participation);
        notificationReminderCreator.createReminder(SESSION_ID, "알림 아웃박스 러닝", REMINDER_RECIPIENT_ID);

        List<Notification> notifications = notificationRepository.findAll();
        List<Map<String, Object>> outboxRows = jdbcTemplate.queryForList(
                "SELECT notification_id, recipient_user_id FROM push_outbox"
        );

        assertThat(notifications).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.values());
        assertThat(outboxRows).hasSize(5);
        assertThat(outboxRows).allSatisfy(row -> {
            long notificationId = ((Number) row.get("notification_id")).longValue();
            long recipientUserId = ((Number) row.get("recipient_user_id")).longValue();
            Notification notification = notifications.stream()
                    .filter(candidate -> candidate.getId().equals(notificationId))
                    .findFirst()
                    .orElseThrow();
            assertThat(recipientUserId).isEqualTo(notification.getRecipientUserId());
        });
    }

    @Test
    void 토큰이_없으면_알림만_저장하고_나중_등록도_과거_아웃박스를_만들지_않는다() {
        notificationService.notifyParticipationRequested(participation());

        assertThat(notificationRepository.count()).isEqualTo(1L);
        assertThat(pushOutboxRepository.count()).isZero();

        registerToken(HOST_ID);

        assertThat(notificationRepository.count()).isEqualTo(1L);
        assertThat(pushOutboxRepository.count()).isZero();
    }

    @Test
    void 강제된_아웃박스_유일성_실패는_알림과_작업을_함께_롤백한다() {
        registerToken(HOST_ID);
        duplicateOnDemandEnqueuer.requestDuplicateOnNextEnqueue();

        assertThatThrownBy(() -> notificationService.notifyParticipationRequested(participation()))
                .isInstanceOf(DataIntegrityViolationException.class);

        entityManager.clear();
        assertThat(notificationRepository.count()).isZero();
        assertThat(pushOutboxRepository.count()).isZero();
    }

    private SessionParticipant participation() {
        User host = User.builder().id(HOST_ID).name("호스트").build();
        User applicant = User.builder().id(APPLICANT_ID).name("신청자").build();
        Session session = Session.builder().id(SESSION_ID).hostUser(host).title("알림 아웃박스 러닝").build();
        return SessionParticipant.builder()
                .id(PARTICIPATION_ID)
                .session(session)
                .user(applicant)
                .build();
    }

    private void registerToken(long userId) {
        pushDeviceTokenRepository.saveAndFlush(PushDeviceToken.builder()
                .userId(userId)
                .token("integration-token-" + userId)
                .platform(PushPlatform.ANDROID)
                .build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DuplicateEnqueuerConfiguration {

        @Bean
        @Primary
        DuplicateOnDemandEnqueuer duplicateOnDemandEnqueuer(
                FcmPushProperties fcmPushProperties,
                PushDeviceTokenRepository pushDeviceTokenRepository,
                PushOutboxRepository pushOutboxRepository,
                Clock clock
        ) {
            return new DuplicateOnDemandEnqueuer(
                    fcmPushProperties,
                    pushDeviceTokenRepository,
                    pushOutboxRepository,
                    clock
            );
        }
    }

    static class DuplicateOnDemandEnqueuer extends PushOutboxEnqueuer {

        private boolean duplicateRequested;

        DuplicateOnDemandEnqueuer(
                FcmPushProperties fcmPushProperties,
                PushDeviceTokenRepository pushDeviceTokenRepository,
                PushOutboxRepository pushOutboxRepository,
                Clock clock
        ) {
            super(fcmPushProperties, pushDeviceTokenRepository, pushOutboxRepository, clock);
        }

        void requestDuplicateOnNextEnqueue() {
            duplicateRequested = true;
        }

        void clearDuplicateRequest() {
            duplicateRequested = false;
        }

        @Override
        public void enqueue(Notification notification) {
            super.enqueue(notification);
            if (duplicateRequested) {
                duplicateRequested = false;
                super.enqueue(notification);
            }
        }
    }
}
