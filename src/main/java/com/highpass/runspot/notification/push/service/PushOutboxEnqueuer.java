package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.push.config.FcmPushProperties;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.outbox.PushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushOutboxEnqueuer {

    private final FcmPushProperties fcmPushProperties;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final PushOutboxRepository pushOutboxRepository;
    private final Clock clock;

    public void enqueue(Notification notification) {
        if (!fcmPushProperties.isEnabled()
                || pushDeviceTokenRepository.findByUserId(notification.getRecipientUserId()).isEmpty()) {
            return;
        }

        pushOutboxRepository.save(PushOutbox.pending(
                notification,
                LocalDateTime.now(clock).plus(fcmPushProperties.getPublishDelay())
        ));
    }
}
