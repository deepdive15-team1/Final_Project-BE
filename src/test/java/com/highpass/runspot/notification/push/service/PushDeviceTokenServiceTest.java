package com.highpass.runspot.notification.push.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.notification.push.domain.PushDeviceToken;
import com.highpass.runspot.notification.push.domain.PushPlatform;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.exception.PushDeviceTokenErrorCode;
import com.highpass.runspot.notification.push.exception.PushDeviceTokenException;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver")
class PushDeviceTokenServiceTest extends MySqlContainerSupport {

    private static final long FIRST_USER_ID = 1001L;
    private static final long SECOND_USER_ID = 1002L;
    private static final int WAIT_SECONDS = 10;

    @Autowired
    private PushDeviceTokenService pushDeviceTokenService;

    @Autowired
    private PushDeviceTokenRepository pushDeviceTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        pushDeviceTokenRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, SECONDS)).isTrue();
        pushDeviceTokenRepository.deleteAll();
    }

    @Test
    void 사용자_토큰을_교체하면_새_토큰_한_개만_남는다() {
        pushDeviceTokenService.upsert(FIRST_USER_ID, "first-token", PushPlatform.ANDROID);

        pushDeviceTokenService.upsert(FIRST_USER_ID, "replacement-token", PushPlatform.ANDROID);

        assertThat(pushDeviceTokenRepository.findByUserId(FIRST_USER_ID).orElseThrow().getToken())
                .isEqualTo("replacement-token");
        assertThat(pushDeviceTokenRepository.count()).isEqualTo(1L);
    }

    @Test
    void 동일_토큰을_다른_사용자가_등록하면_소유권이_이전된다() {
        pushDeviceTokenService.upsert(FIRST_USER_ID, "shared-token", PushPlatform.ANDROID);
        pushDeviceTokenService.upsert(SECOND_USER_ID, "previous-second-user-token", PushPlatform.ANDROID);

        pushDeviceTokenService.upsert(SECOND_USER_ID, "shared-token", PushPlatform.ANDROID);

        assertThat(pushDeviceTokenRepository.findByUserId(FIRST_USER_ID)).isEmpty();
        assertThat(pushDeviceTokenRepository.findByUserId(SECOND_USER_ID).orElseThrow().getToken())
                .isEqualTo("shared-token");
        assertThat(pushDeviceTokenRepository.count()).isEqualTo(1L);
    }

    @Test
    void 동일_등록은_새_행을_만들지_않는다() {
        pushDeviceTokenService.upsert(FIRST_USER_ID, "idempotent-token", PushPlatform.ANDROID);

        pushDeviceTokenService.upsert(FIRST_USER_ID, "idempotent-token", PushPlatform.ANDROID);

        assertThat(pushDeviceTokenRepository.count()).isEqualTo(1L);
        assertThat(pushDeviceTokenRepository.findByUserId(FIRST_USER_ID).orElseThrow().getToken())
                .isEqualTo("idempotent-token");
    }

    @Test
    void 존재하지_않거나_이미_삭제된_토큰_삭제는_멱등적이다() {
        pushDeviceTokenService.delete(SECOND_USER_ID);
        pushDeviceTokenService.upsert(FIRST_USER_ID, "deletable-token", PushPlatform.ANDROID);

        pushDeviceTokenService.delete(FIRST_USER_ID);
        pushDeviceTokenService.delete(FIRST_USER_ID);

        assertThat(pushDeviceTokenRepository.count()).isZero();
    }

    @Test
    void 사용자별_유일_제약을_데이터베이스가_강제한다() {
        pushDeviceTokenRepository.saveAndFlush(PushDeviceToken.builder()
                .userId(FIRST_USER_ID)
                .token("first-token")
                .platform(PushPlatform.ANDROID)
                .build());

        assertThatThrownBy(() -> pushDeviceTokenRepository.saveAndFlush(PushDeviceToken.builder()
                .userId(FIRST_USER_ID)
                .token("second-token")
                .platform(PushPlatform.ANDROID)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 토큰별_유일_제약을_데이터베이스가_강제한다() {
        pushDeviceTokenRepository.saveAndFlush(PushDeviceToken.builder()
                .userId(FIRST_USER_ID)
                .token("shared-token")
                .platform(PushPlatform.ANDROID)
                .build());

        assertThatThrownBy(() -> pushDeviceTokenRepository.saveAndFlush(PushDeviceToken.builder()
                .userId(SECOND_USER_ID)
                .token("shared-token")
                .platform(PushPlatform.ANDROID)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 토큰_열의_길이와_플랫폼_열의_문자열_형식을_스키마가_보장한다() {
        Integer tokenLength = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_device_tokens' "
                        + "AND column_name = 'token'",
                Integer.class);
        Integer platformLength = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_device_tokens' "
                        + "AND column_name = 'platform'",
                Integer.class);
        String platformType = jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_device_tokens' "
                        + "AND column_name = 'platform'",
                String.class);

        assertThat(tokenLength).isEqualTo(512);
        assertThat(platformLength).isEqualTo(20);
        assertThat(platformType).isEqualTo("varchar");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO push_device_tokens (user_id, token, platform, created_at, updated_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                FIRST_USER_ID,
                "unsupported-platform-token",
                "IOS"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void 스키마는_사용자와_토큰에_명시적_유일_제약을_둔다() {
        List<String> constraintNames = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = 'push_device_tokens' "
                        + "AND constraint_type = 'UNIQUE'",
                String.class);

        assertThat(constraintNames)
                .containsExactlyInAnyOrder("uk_push_device_tokens_user_id", "uk_push_device_tokens_token");
    }

    @RepeatedTest(2)
    @Timeout(60)
    void 동일_토큰을_동시에_등록해도_한_행과_한_소유자만_남는다() throws Exception {
        String token = "concurrent-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<RegistrationOutcome> first = executor.submit(
                () -> registerWhenStarted(FIRST_USER_ID, token, ready, start));
        Future<RegistrationOutcome> second = executor.submit(
                () -> registerWhenStarted(SECOND_USER_ID, token, ready, start));

        assertThat(ready.await(WAIT_SECONDS, SECONDS)).isTrue();
        start.countDown();
        List<RegistrationOutcome> outcomes = List.of(
                first.get(WAIT_SECONDS, SECONDS),
                second.get(WAIT_SECONDS, SECONDS));

        assertThat(outcomes).allMatch(outcome -> outcome == RegistrationOutcome.REGISTERED
                || outcome == RegistrationOutcome.CONFLICT);
        assertThat(pushDeviceTokenRepository.findAll())
                .extracting(PushDeviceToken::getToken)
                .containsExactly(token);
        assertThat(pushDeviceTokenRepository.findByToken(token).orElseThrow().getUserId())
                .isIn(FIRST_USER_ID, SECOND_USER_ID);
    }

    private RegistrationOutcome registerWhenStarted(
            long userId,
            String token,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(WAIT_SECONDS, SECONDS)) {
            throw new IllegalStateException("동시 토큰 등록 시작 신호를 기다리는 중 시간 초과되었습니다.");
        }
        try {
            pushDeviceTokenService.upsert(userId, token, PushPlatform.ANDROID);
            return RegistrationOutcome.REGISTERED;
        } catch (PushDeviceTokenException exception) {
            assertThat(exception.getExceptionType())
                    .isEqualTo(PushDeviceTokenErrorCode.TOKEN_REGISTRATION_CONFLICT);
            return RegistrationOutcome.CONFLICT;
        }
    }

    private enum RegistrationOutcome {
        REGISTERED,
        CONFLICT
    }
}
