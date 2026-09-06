package com.highpass.runspot.notification.push.config;

import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService;
import com.highpass.runspot.notification.push.outbox.PushOutboxCleanupService;
import com.highpass.runspot.notification.push.outbox.PushOutboxFinalizeService;
import com.highpass.runspot.notification.push.service.PushBackoffPolicy;
import com.highpass.runspot.notification.push.service.PushDeliveryPreparationService;
import com.highpass.runspot.notification.push.service.PushDeliveryWorker;
import com.highpass.runspot.notification.push.service.PushOutboxCleanupScheduler;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "push.fcm", name = "enabled", havingValue = "true")
public class PushDeliverySchedulingConfig {

    @Bean
    PushDeliveryWorker pushDeliveryWorker(
            FcmPushProperties fcmPushProperties,
            PushOutboxClaimService claimService,
            PushDeliveryPreparationService preparationService,
            PushOutboxFinalizeService finalizeService,
            PushMessagingGateway pushMessagingGateway,
            PushBackoffPolicy pushBackoffPolicy,
            Clock clock
    ) {
        return new PushDeliveryWorker(
                fcmPushProperties,
                claimService,
                preparationService,
                finalizeService,
                pushMessagingGateway,
                pushBackoffPolicy,
                clock
        );
    }

    @Bean
    PushOutboxCleanupScheduler pushOutboxCleanupScheduler(PushOutboxCleanupService cleanupService, Clock clock) {
        return new PushOutboxCleanupScheduler(cleanupService, clock);
    }
}
