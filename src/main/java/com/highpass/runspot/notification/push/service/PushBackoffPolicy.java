package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.push.config.FcmPushProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushBackoffPolicy {

    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal MAX_DURATION_NANOS = BigDecimal.valueOf(Long.MAX_VALUE)
            .multiply(NANOS_PER_SECOND)
            .add(BigDecimal.valueOf(999_999_999L));

    private final FcmPushProperties fcmPushProperties;
    private final PushJitterSource jitterSource;

    public Duration delayForFailedAttempt(int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("실패 시도 횟수는 양수여야 합니다.");
        }
        Duration maximum = fcmPushProperties.getMaxBackoff();
        Duration jittered = applyJitter(cappedExponentialDelay(attemptNumber));
        return jittered.compareTo(maximum) > 0 ? maximum : jittered;
    }

    public boolean isExhausted(int completedAttemptNumber) {
        return completedAttemptNumber >= fcmPushProperties.getMaxAttempts();
    }

    private Duration cappedExponentialDelay(int attemptNumber) {
        Duration maximum = fcmPushProperties.getMaxBackoff();
        Duration delay = fcmPushProperties.getInitialBackoff();
        for (int attempt = 1; attempt < attemptNumber && delay.compareTo(maximum) < 0; attempt++) {
            delay = doubleOrCap(delay, maximum);
        }
        return delay.compareTo(maximum) > 0 ? maximum : delay;
    }

    private Duration doubleOrCap(Duration delay, Duration maximum) {
        if (delay.compareTo(maximum.dividedBy(2)) > 0) {
            return maximum;
        }
        return delay.multipliedBy(2);
    }

    private Duration applyJitter(Duration delay) {
        double random = Math.max(0d, Math.min(1d, jitterSource.nextDouble()));
        double jitter = fcmPushProperties.getJitter();
        BigDecimal multiplier = BigDecimal.valueOf(1d - jitter + (2d * jitter * random));
        BigDecimal nanos = BigDecimal.valueOf(delay.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigDecimal.valueOf(delay.getNano()))
                .multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP);
        if (nanos.compareTo(MAX_DURATION_NANOS) >= 0) {
            return Duration.ofSeconds(Long.MAX_VALUE, 999_999_999L);
        }
        BigDecimal[] secondsAndNanos = nanos.divideAndRemainder(NANOS_PER_SECOND);
        return Duration.ofSeconds(secondsAndNanos[0].longValueExact(), secondsAndNanos[1].longValueExact());
    }
}
