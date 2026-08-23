package com.highpass.runspot.notification.service;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.common.util.GeometryUtil;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.session.domain.GenderPolicy;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.RunType;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.SessionStatus;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import(NotificationReminderIntegrationSupport.FixedClockConfiguration.class)
abstract class NotificationReminderIntegrationSupport extends MySqlContainerSupport {

    protected static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 23, 9, 0);
    private static final AtomicLong USER_SEQUENCE = new AtomicLong();

    @Autowired
    protected NotificationReminderScheduler scheduler;
    @Autowired
    protected NotificationReminderCreator creator;
    @Autowired
    protected NotificationRepository notificationRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected SessionRepository sessionRepository;
    @Autowired
    protected SessionParticipantRepository sessionParticipantRepository;

    @BeforeEach
    void cleanReminderData() {
        notificationRepository.deleteAll();
        sessionParticipantRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    protected User user(String label) {
        long sequence = USER_SEQUENCE.incrementAndGet();
        return userRepository.saveAndFlush(User.builder()
                .username("reminder-" + label + "-" + sequence)
                .password("password")
                .name(label)
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build());
    }

    protected Session session(User host, LocalDateTime startAt, SessionStatus status) {
        return sessionRepository.saveAndFlush(Session.builder()
                .hostUser(host)
                .title("한강 야간 러닝")
                .runType(RunType.RECOVERY)
                .locationName("테스트 장소")
                .location(GeometryUtil.createPoint(BigDecimal.ZERO, BigDecimal.ZERO))
                .routePolyline(List.of(new Session.RoutePoint(BigDecimal.ZERO, BigDecimal.ZERO)))
                .targetDistanceKm(BigDecimal.ONE)
                .avgPaceSec(360)
                .startAt(startAt)
                .capacity(10)
                .genderPolicy(GenderPolicy.MIXED)
                .status(status)
                .build());
    }

    protected SessionParticipant participant(Session session, User user, ParticipationStatus status) {
        return sessionParticipantRepository.saveAndFlush(SessionParticipant.builder()
                .session(session)
                .user(user)
                .status(status)
                .build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock reminderClock() {
            return Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }
}
