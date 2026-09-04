package com.highpass.runspot.notification.push.outbox;

import com.highpass.runspot.notification.push.config.FcmPushProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushOutboxCleanupService {

    private final PushOutboxRepository pushOutboxRepository;
    private final FcmPushProperties fcmPushProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredTerminalRows(LocalDateTime now) {
        return pushOutboxRepository.deleteTerminalBefore(now.minus(fcmPushProperties.getTerminalRetention()));
    }
}
