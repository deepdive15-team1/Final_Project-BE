package com.highpass.runspot.notification.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
public class NotificationSchedulingConfig {

    @Bean
    public Clock notificationClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @EnableScheduling
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "notification.reminder",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class ReminderSchedulingEnablement {
    }
}
