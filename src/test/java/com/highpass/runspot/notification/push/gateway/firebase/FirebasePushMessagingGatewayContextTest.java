package com.highpass.runspot.notification.push.gateway.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.firebase.messaging.FirebaseMessaging;
import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

class FirebasePushMessagingGatewayContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayComponentScanConfiguration.class);

    @Test
    void componentScanningCreatesMapperAndClassifierWithoutFirebaseMessaging() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AndroidPushMessageMapper.class);
            assertThat(context).hasSingleBean(FirebaseErrorClassifier.class);
            assertThat(context).doesNotHaveBean(PushMessagingGateway.class);
        });
    }

    @Test
    void componentScanningCreatesGatewayOnlyWhenFirebaseMessagingExists() {
        contextRunner.withBean(FirebaseMessaging.class, () -> mock(FirebaseMessaging.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AndroidPushMessageMapper.class);
            assertThat(context).hasSingleBean(FirebaseErrorClassifier.class);
            assertThat(context).hasSingleBean(PushMessagingGateway.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = FirebasePushMessagingGateway.class)
    static class GatewayComponentScanConfiguration {
    }
}
