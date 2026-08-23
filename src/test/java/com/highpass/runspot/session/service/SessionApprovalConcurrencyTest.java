package com.highpass.runspot.session.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.common.util.GeometryUtil;
import com.highpass.runspot.session.domain.GenderPolicy;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.RunType;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class SessionApprovalConcurrencyTest extends MySqlContainerSupport {

    private static final int WAIT_SECONDS = 10;

    @Autowired
    private SessionService sessionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionParticipantRepository sessionParticipantRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        sessionParticipantRepository.deleteAll();
        sessionRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, SECONDS)).isTrue();
        sessionParticipantRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    @Timeout(60)
    void 마지막_한_자리를_동시에_승인하면_한_명만_APPROVED가_된다() throws Exception {
        User host = userRepository.saveAndFlush(user("host", "호스트"));
        User firstApplicant = userRepository.saveAndFlush(user("first", "첫번째"));
        User secondApplicant = userRepository.saveAndFlush(user("second", "두번째"));
        Session session = sessionRepository.saveAndFlush(session(host));
        SessionParticipant firstRequest = sessionParticipantRepository.saveAndFlush(request(session, firstApplicant));
        SessionParticipant secondRequest = sessionParticipantRepository.saveAndFlush(request(session, secondApplicant));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<ApprovalOutcome> first = executor.submit(
                () -> approveWhenStarted(host.getId(), session.getId(), firstRequest.getId(), ready, start));
        Future<ApprovalOutcome> second = executor.submit(
                () -> approveWhenStarted(host.getId(), session.getId(), secondRequest.getId(), ready, start));

        assertThat(ready.await(WAIT_SECONDS, SECONDS)).isTrue();
        start.countDown();
        List<ApprovalOutcome> outcomes = List.of(
                first.get(WAIT_SECONDS, SECONDS),
                second.get(WAIT_SECONDS, SECONDS));

        assertThat(outcomes).containsExactlyInAnyOrder(ApprovalOutcome.APPROVED, ApprovalOutcome.CAPACITY_REJECTED);
        List<SessionParticipant> persisted = sessionParticipantRepository.findBySessionId(session.getId());
        long approvedCount = persisted.stream()
                .filter(participant -> participant.getStatus() == ParticipationStatus.APPROVED)
                .count();
        assertThat(approvedCount).isEqualTo(1L);
        assertThat(approvedCount).isLessThanOrEqualTo(session.getCapacity());
        assertThat(persisted).extracting(SessionParticipant::getStatus)
                .containsExactlyInAnyOrder(ParticipationStatus.APPROVED, ParticipationStatus.REQUESTED);
    }

    private ApprovalOutcome approveWhenStarted(
            Long hostId,
            Long sessionId,
            Long participationId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(WAIT_SECONDS, SECONDS)) {
            throw new IllegalStateException("동시 승인 시작 신호를 기다리는 중 시간 초과되었습니다.");
        }
        try {
            sessionService.approveJoinRequest(hostId, sessionId, participationId);
            return ApprovalOutcome.APPROVED;
        } catch (IllegalStateException exception) {
            assertThat(exception).hasMessage("모집 인원이 마감되어 승인할 수 없습니다.");
            return ApprovalOutcome.CAPACITY_REJECTED;
        }
    }

    private User user(String username, String name) {
        return User.builder()
                .username(username)
                .password("password")
                .name(name)
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build();
    }

    private Session session(User host) {
        return Session.builder()
                .hostUser(host)
                .title("동시 승인 테스트")
                .runType(RunType.RECOVERY)
                .locationName("테스트 장소")
                .location(GeometryUtil.createPoint(BigDecimal.ZERO, BigDecimal.ZERO))
                .routePolyline(List.of(new Session.RoutePoint(BigDecimal.ZERO, BigDecimal.ZERO)))
                .targetDistanceKm(BigDecimal.ONE)
                .avgPaceSec(360)
                .startAt(LocalDateTime.now().plusDays(1))
                .capacity(1)
                .genderPolicy(GenderPolicy.MIXED)
                .build();
    }

    private SessionParticipant request(Session session, User applicant) {
        return SessionParticipant.builder()
                .session(session)
                .user(applicant)
                .build();
    }

    private enum ApprovalOutcome {
        APPROVED,
        CAPACITY_REJECTED
    }
}
