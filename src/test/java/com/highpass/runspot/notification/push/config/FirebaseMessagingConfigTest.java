package com.highpass.runspot.notification.push.config;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FirebaseMessagingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FirebaseMessagingConfig.class);

    @Test
    void FCM이_비활성화되면_ADC없이_기본값으로_시작하고_Firebase_빈을_생성하지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FirebaseApp.class);
            assertThat(context).doesNotHaveBean(FirebaseMessaging.class);

            FcmPushProperties properties = context.getBean(FcmPushProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getBatchSize()).isEqualTo(100);
            assertThat(properties.getPublishDelay()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.getMaxAttempts()).isEqualTo(5);
            assertThat(properties.getInitialBackoff()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getMaxBackoff()).isEqualTo(Duration.ofSeconds(300));
            assertThat(properties.getJitter()).isEqualTo(0.2d);
            assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofSeconds(120));
            assertThat(properties.getTerminalRetention()).isEqualTo(Duration.ofDays(30));
            assertThat(properties.getCleanupCron()).isEqualTo("0 15 3 * * *");
            assertThat(properties.getCleanupZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
        });
    }

    @Test
    void FCM이_활성화되면_하나의_FirebaseApp과_Messaging_빈을_생성한다() {
        try (MockedStatic<GoogleCredentials> credentials = mockStatic(GoogleCredentials.class)) {
            credentials.when(GoogleCredentials::getApplicationDefault).thenReturn(mock(GoogleCredentials.class));

            contextRunner.withPropertyValues("push.fcm.enabled=true").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(FirebaseApp.class);
                assertThat(context).hasSingleBean(FirebaseMessaging.class);
            });
        }

        assertThat(FirebaseApp.getApps()).isEmpty();
    }

    @Test
    void 존재하지_않는_ADC_경로로_FCM을_활성화하면_컨텍스트_시작에_실패한다() throws Exception {
        Path missingCredentialsPath = Path.of("build", "missing-firebase-adc-" + UUID.randomUUID() + ".json")
                .toAbsolutePath();

        withEnvironmentVariable("GOOGLE_APPLICATION_CREDENTIALS", missingCredentialsPath.toString())
                .execute(() -> contextRunner.withPropertyValues("push.fcm.enabled=true").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                }));
    }

    @Test
    void 읽을수없는_ADC_파일로_FCM을_활성화하면_컨텍스트_시작에_실패한다() throws Exception {
        Path unreadableCredentialsPath = Files.createTempFile("runspot-firebase-adc-", ".json");

        try {
            Files.writeString(unreadableCredentialsPath, "{}");
            Files.setPosixFilePermissions(unreadableCredentialsPath, Set.of());

            withEnvironmentVariable("GOOGLE_APPLICATION_CREDENTIALS", unreadableCredentialsPath.toString())
                    .execute(() -> contextRunner.withPropertyValues("push.fcm.enabled=true").run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).isNotNull();
                    }));
        } finally {
            Files.setPosixFilePermissions(unreadableCredentialsPath, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            Files.deleteIfExists(unreadableCredentialsPath);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 501})
    void 배치_크기가_범위를_벗어나면_바인딩에_실패한다(int batchSize) {
        assertInvalidProperty("push.fcm.batch-size=" + batchSize);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "push.fcm.publish-delay=0s",
            "push.fcm.publish-delay=-1s",
            "push.fcm.initial-backoff=0s",
            "push.fcm.initial-backoff=-1s",
            "push.fcm.max-backoff=0s",
            "push.fcm.max-backoff=-1s",
            "push.fcm.lease-duration=0s",
            "push.fcm.lease-duration=-1s",
            "push.fcm.terminal-retention=0s",
            "push.fcm.terminal-retention=-1s"
    })
    void 지연과_보존_기간이_양수가_아니면_바인딩에_실패한다(String property) {
        assertInvalidProperty(property);
    }

    @Test
    void 최대_시도_횟수가_양수가_아니면_바인딩에_실패한다() {
        assertInvalidProperty("push.fcm.max-attempts=0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01d, 1.01d})
    void 지터가_0과_1_사이가_아니면_바인딩에_실패한다(double jitter) {
        assertInvalidProperty("push.fcm.jitter=" + jitter);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "push.fcm.batch-size=not-a-number",
            "push.fcm.publish-delay=not-a-duration"
    })
    void 설정값_형식이_잘못되면_바인딩에_실패한다(String property) {
        assertInvalidProperty(property);
    }

    private void assertInvalidProperty(String property) {
        contextRunner.withPropertyValues(property).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
        });
    }
}
