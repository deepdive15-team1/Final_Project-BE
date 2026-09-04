package com.highpass.runspot.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.notification.push.config.FcmPushProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PushBackoffPolicyTest {

    @Test
    void injectedJitterProducesDeterministicLowerMiddleAndUpperBounds() {
        assertThat(policy(0d, 0.2d).delayForFailedAttempt(1)).isEqualTo(Duration.ofSeconds(4));
        assertThat(policy(0.5d, 0.2d).delayForFailedAttempt(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(policy(1d, 0.2d).delayForFailedAttempt(1)).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void exponentialSequenceCapsWithoutAnUnboundedExponent() {
        PushBackoffPolicy policy = policy(0.5d, 0d);

        assertThat(java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(policy::delayForFailedAttempt)
                .toList())
                .containsExactly(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(20),
                        Duration.ofSeconds(40),
                        Duration.ofSeconds(80),
                        Duration.ofSeconds(160),
                        Duration.ofSeconds(300),
                        Duration.ofSeconds(300),
                        Duration.ofSeconds(300),
                        Duration.ofSeconds(300)
                );
    }

    @Test
    void upperJitterAtTheCapNeverExceedsConfiguredMaximum() {
        assertThat(policy(1d, 0.2d).delayForFailedAttempt(10)).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void exhaustionUsesTheConfiguredMaximumAttemptCount() {
        PushBackoffPolicy policy = policy(0.5d, 0d);

        assertThat(policy.isExhausted(4)).isFalse();
        assertThat(policy.isExhausted(5)).isTrue();
    }

    private PushBackoffPolicy policy(double random, double jitter) {
        FcmPushProperties properties = new FcmPushProperties();
        properties.setInitialBackoff(Duration.ofSeconds(5));
        properties.setMaxBackoff(Duration.ofSeconds(300));
        properties.setJitter(jitter);
        properties.setMaxAttempts(5);
        return new PushBackoffPolicy(properties, () -> random);
    }
}
