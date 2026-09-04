package com.highpass.runspot.notification.push.outbox;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushOutboxCleanupService {

    private static final int RETENTION_DAYS = 30;

    private final PushOutboxRepository pushOutboxRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredTerminalRows(LocalDateTime now) {
        return pushOutboxRepository.deleteTerminalBefore(now.minusDays(RETENTION_DAYS));
    }
}
