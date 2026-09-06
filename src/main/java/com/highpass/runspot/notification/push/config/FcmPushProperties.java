package com.highpass.runspot.notification.push.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "push.fcm")
public class FcmPushProperties {

    private boolean enabled = false;

    @Min(1)
    @Max(500)
    private int batchSize = 100;

    @NotNull
    private Duration publishDelay = Duration.ofSeconds(1);

    @Positive
    private int maxAttempts = 5;

    @NotNull
    private Duration initialBackoff = Duration.ofSeconds(5);

    @NotNull
    private Duration maxBackoff = Duration.ofSeconds(300);

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double jitter = 0.2d;

    @NotNull
    private Duration leaseDuration = Duration.ofSeconds(120);

    @NotNull
    private Duration terminalRetention = Duration.ofDays(30);

    @NotBlank
    private String cleanupCron = "0 15 3 * * *";

    @NotNull
    private ZoneId cleanupZoneId = ZoneId.of("Asia/Seoul");

    @AssertTrue(message = "FCM duration settings must be positive")
    public boolean isDurationConfigurationPositive() {
        return isPositive(publishDelay)
                && isPositive(initialBackoff)
                && isPositive(maxBackoff)
                && isPositive(leaseDuration)
                && isPositive(terminalRetention);
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
