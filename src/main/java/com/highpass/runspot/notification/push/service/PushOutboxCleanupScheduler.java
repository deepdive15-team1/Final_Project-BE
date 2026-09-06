package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.push.outbox.PushOutboxCleanupService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class PushOutboxCleanupScheduler {

    private final PushOutboxCleanupService cleanupService;
    private final Clock clock;

    @Scheduled(cron = "${push.fcm.cleanup-cron}", zone = "${push.fcm.cleanup-zone-id}")
    public void cleanExpiredTerminalRows() {
        cleanupService.deleteExpiredTerminalRows(LocalDateTime.now(clock));
    }
}
