package com.highpass.runspot.notification.push.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService;
import com.highpass.runspot.notification.push.outbox.PushOutboxCleanupService;
import com.highpass.runspot.notification.push.outbox.PushOutboxFinalizeService;
import com.highpass.runspot.notification.push.service.PushBackoffPolicy;
import com.highpass.runspot.notification.push.service.PushDeliveryPreparationService;
import com.highpass.runspot.notification.push.service.PushDeliveryWorker;
import com.highpass.runspot.notification.push.service.PushJitterSource;
import com.highpass.runspot.notification.push.service.PushOutboxCleanupScheduler;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PushDeliverySchedulingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, PushDeliverySchedulingConfig.class)
            .withBean(PushOutboxClaimService.class, () -> mock(PushOutboxClaimService.class))
            .withBean(PushDeliveryPreparationService.class, () -> mock(PushDeliveryPreparationService.class))
            .withBean(PushOutboxFinalizeService.class, () -> mock(PushOutboxFinalizeService.class))
            .withBean(PushOutboxCleanupService.class, () -> mock(PushOutboxCleanupService.class))
            .withBean(PushMessagingGateway.class, () -> mock(PushMessagingGateway.class))
            .withBean(PushJitterSource.class, () -> () -> 0.5d)
            .withBean(Clock.class, () -> Clock.system(ZoneId.of("Asia/Seoul")));

    @Test
    void FCMEnabledCreatesPushWorkersEvenWhenReminderSchedulingIsDisabled() {
        contextRunner.withPropertyValues(
                "push.fcm.enabled=true",
                "notification.reminder.enabled=false"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PushDeliveryWorker.class);
            assertThat(context).hasSingleBean(PushOutboxCleanupScheduler.class);
        });
    }

    @Test
    void FCMDisabledCreatesNoPushWorkersWithoutFirebaseOrNetworkBeans() {
        contextRunner.withPropertyValues("push.fcm.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PushDeliveryWorker.class);
            assertThat(context).doesNotHaveBean(PushOutboxCleanupScheduler.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FcmPushProperties.class)
    static class PropertiesConfiguration {

        @Bean
        PushBackoffPolicy pushBackoffPolicy(FcmPushProperties properties, PushJitterSource jitterSource) {
            return new PushBackoffPolicy(properties, jitterSource);
        }
    }
}
