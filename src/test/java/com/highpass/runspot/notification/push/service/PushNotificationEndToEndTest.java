package com.highpass.runspot.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.common.jwt.JwtProvider;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import com.highpass.runspot.notification.push.outbox.PushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxStatus;
import com.highpass.runspot.notification.service.NotificationReminderCreator;
import com.highpass.runspot.notification.service.NotificationService;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "push.fcm.enabled=true",
        "push.fcm.publish-delay=1d",
        "push.fcm.jitter=0"
})
@AutoConfigureMockMvc
@Import(PushNotificationEndToEndTest.EndToEndConfiguration.class)
class PushNotificationEndToEndTest extends MySqlContainerSupport {

    private static final long SESSION_ID = 2101L;
    private static final long PARTICIPATION_ID = 2201L;

    @MockitoBean
    private FirebaseApp firebaseApp;
    @MockitoBean
    private FirebaseMessaging firebaseMessaging;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationReminderCreator notificationReminderCreator;
    @Autowired
    private PushDeliveryWorker worker;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PushOutboxRepository pushOutboxRepository;
    @Autowired
    private PushDeviceTokenRepository pushDeviceTokenRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EndToEndClock clock;
    @Autowired
    private SuccessGateway gateway;

    @BeforeEach
    @AfterEach
    void cleanData() {
        clock.reset();
        gateway.reset();
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
        pushDeviceTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void allCurrentNotificationTypesCompleteAuthenticatedRegisterEnqueueClaimFakeSendAndSent() throws Exception {
        User host = user("호스트");
        User applicant = user("신청자");
        User reminderRecipient = user("리마인더");
        registerTokenThroughAuthenticatedApi(host);
        registerTokenThroughAuthenticatedApi(applicant);
        registerTokenThroughAuthenticatedApi(reminderRecipient);
        SessionParticipant participation = participation(host, applicant);

        notificationService.notifyParticipationRequested(participation);
        notificationService.notifyParticipationApproved(participation);
        notificationService.notifyParticipationRejected(participation);
        notificationService.notifyParticipantKicked(participation);
        notificationReminderCreator.createReminder(SESSION_ID, "종단간 러닝", reminderRecipient.getId());
        clock.advance(Duration.ofDays(1));

        worker.deliverDuePushes();

        assertThat(notificationRepository.findAll()).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.values());
        assertThat(pushOutboxRepository.findAll()).hasSize(5)
                .extracting(PushOutbox::getStatus)
                .containsOnly(PushOutboxStatus.SENT);
        assertThat(gateway.sentTypes).containsExactlyInAnyOrder(NotificationType.values());
        assertThat(gateway.transactionStates).containsOnly(false);
        assertThat(pushDeviceTokenRepository.findByUserId(host.getId())).isPresent();
    }

    private SessionParticipant participation(User host, User applicant) {
        Session session = Session.builder().id(SESSION_ID).hostUser(host).title("종단간 러닝").build();
        return SessionParticipant.builder()
                .id(PARTICIPATION_ID)
                .session(session)
                .user(applicant)
                .build();
    }

    private User user(String name) {
        return userRepository.saveAndFlush(User.builder()
                .username("push-e2e-" + name)
                .password("password")
                .name(name)
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build());
    }

    private void registerTokenThroughAuthenticatedApi(User user) throws Exception {
        String token = "e2e-token-" + user.getId();
        mockMvc.perform(put("/users/me/push-token")
                        .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(user.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isNoContent());
        assertThat(pushDeviceTokenRepository.findByUserId(user.getId()).orElseThrow().getToken()).isEqualTo(token);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EndToEndConfiguration {

        @Bean
        @Primary
        EndToEndClock endToEndClock() {
            return new EndToEndClock();
        }

        @Bean
        @Primary
        PushJitterSource deterministicPushJitterSource() {
            return () -> 0.5d;
        }

        @Bean
        @Primary
        SuccessGateway successGateway() {
            return new SuccessGateway();
        }
    }

    static class EndToEndClock extends Clock {

        private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
        private Instant instant = Instant.parse("2026-09-05T03:00:00Z");

        void reset() {
            instant = Instant.parse("2026-09-05T03:00:00Z");
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    static class SuccessGateway implements PushMessagingGateway {

        private final List<NotificationType> sentTypes = new ArrayList<>();
        private final List<Boolean> transactionStates = new ArrayList<>();

        @Override
        public List<PushSendResult> send(List<PushMessage> messages) {
            transactionStates.add(isActualTransactionActive());
            sentTypes.addAll(messages.stream().map(PushMessage::type).toList());
            return messages.stream().map(message -> PushSendResult.success(message.notificationId())).toList();
        }

        void reset() {
            sentTypes.clear();
            transactionStates.clear();
        }
    }
}
