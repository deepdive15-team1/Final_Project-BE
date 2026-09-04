package com.highpass.runspot.notification.push.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PushOutboxStateTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);

    @Autowired
    private PushOutboxRepository pushOutboxRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PushOutboxClaimService claimService;

    @Autowired
    private PushOutboxFinalizeService finalizeService;

    @Autowired
    private PushOutboxCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void claimRequiresDueWorkAndDoesNotIncreaseAttempts() {
        PushOutbox outbox = PushOutbox.pending(notification("domain"), NOW.plusMinutes(1));

        assertThat(outbox.isClaimable(NOW)).isFalse();
        assertThatThrownBy(() -> outbox.claim(UUID.randomUUID(), NOW, NOW.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);

        UUID firstLease = UUID.randomUUID();
        outbox.claim(firstLease, NOW.plusMinutes(1), NOW.plusMinutes(3));

        assertThat(outbox.getStatus()).isEqualTo(PushOutboxStatus.PROCESSING);
        assertThat(outbox.getAttempts()).isZero();
        assertThat(outbox.isClaimable(NOW.plusMinutes(2))).isFalse();
        assertThat(outbox.isClaimable(NOW.plusMinutes(3))).isTrue();
    }

    @Test
    void claimRejectsBatchSizesOutsideTheConfiguredRange() {
        assertThatThrownBy(() -> claimService.claim(0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> claimService.claim(501, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attemptsIncreaseOnlyWhenAnActualSendResultIsFinalized() {
        persistPending("retry", NOW);
        persistPending("no-token", NOW);
        List<ClaimedPushOutbox> claims = claimService.claim(2, NOW).stream()
                .sorted(Comparator.comparing(ClaimedPushOutbox::outboxId))
                .toList();
        LocalDateTime retryAt = NOW.plusMinutes(5);

        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getAttempts).containsOnly(0);
        assertThat(finalizeService.reschedule(claims.get(0), retryAt, "UNAVAILABLE")).isTrue();
        assertThat(finalizeService.markFailedWithoutAttempt(claims.get(1), NOW, "TOKEN_NOT_FOUND")).isTrue();

        PushOutbox retry = pushOutboxRepository.findById(claims.get(0).outboxId()).orElseThrow();
        PushOutbox noToken = pushOutboxRepository.findById(claims.get(1).outboxId()).orElseThrow();
        assertThat(retry.getAttempts()).isEqualTo(1);
        assertThat(retry.getStatus()).isEqualTo(PushOutboxStatus.PENDING);
        assertThat(retry.getLastErrorCode()).isEqualTo("UNAVAILABLE");
        assertThat(retry.getLeaseToken()).isNull();
        assertThat(retry.getLeaseUntil()).isNull();
        assertThat(noToken.getAttempts()).isZero();
        assertThat(noToken.getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(noToken.getTerminalAt()).isEqualTo(NOW);
        assertThat(noToken.getLastErrorCode()).isEqualTo("TOKEN_NOT_FOUND");

        ClaimedPushOutbox secondAttempt = claimService.claim(1, retryAt).get(0);
        assertThat(secondAttempt.attemptNumber()).isEqualTo(2);
        assertThat(finalizeService.markSent(secondAttempt, retryAt.plusSeconds(1))).isTrue();

        PushOutbox sent = pushOutboxRepository.findById(secondAttempt.outboxId()).orElseThrow();
        assertThat(sent.getAttempts()).isEqualTo(2);
        assertThat(sent.getStatus()).isEqualTo(PushOutboxStatus.SENT);
        assertThat(sent.getSentAt()).isEqualTo(retryAt.plusSeconds(1));
        assertThat(sent.getTerminalAt()).isEqualTo(retryAt.plusSeconds(1));
        assertThat(sent.getLastErrorCode()).isNull();
        assertThat(sent.getLeaseToken()).isNull();
        assertThat(sent.getLeaseUntil()).isNull();
        assertThat(claimService.claim(10, retryAt.plusDays(1))).isEmpty();
    }

    @Test
    void cleanupDeletesOnlyOldTerminalRows() {
        persistPending("old-sent", NOW.minusDays(31));
        persistPending("old-failed", NOW.minusDays(31));
        persistPending("recent-sent", NOW.minusDays(31));
        persistPending("processing", NOW.minusDays(31));
        persistPending("pending", NOW.plusDays(1));
        List<ClaimedPushOutbox> claims = claimService.claim(4, NOW.minusDays(31));

        assertThat(finalizeService.markSent(claims.get(0), NOW.minusDays(31))).isTrue();
        assertThat(finalizeService.markFailed(claims.get(1), NOW.minusDays(31), "INVALID_ARGUMENT")).isTrue();
        assertThat(finalizeService.markSent(claims.get(2), NOW.minusDays(29))).isTrue();

        assertThat(cleanupService.deleteExpiredTerminalRows(NOW)).isEqualTo(2);

        List<PushOutbox> remaining = pushOutboxRepository.findAll();
        assertThat(remaining).hasSize(3);
        assertThat(remaining).extracting(PushOutbox::getStatus)
                .containsExactlyInAnyOrder(
                        PushOutboxStatus.SENT,
                        PushOutboxStatus.PROCESSING,
                        PushOutboxStatus.PENDING
                );
    }

    @Test
    void cleanupRetainsTerminalRowsAtTheThirtyDayBoundary() {
        persistPending("boundary", NOW.minusDays(30));
        ClaimedPushOutbox claim = claimService.claim(1, NOW.minusDays(30)).get(0);
        assertThat(finalizeService.markSent(claim, NOW.minusDays(30))).isTrue();

        assertThat(cleanupService.deleteExpiredTerminalRows(NOW)).isZero();

        PushOutbox retained = pushOutboxRepository.findById(claim.outboxId()).orElseThrow();
        assertThat(retained.getStatus()).isEqualTo(PushOutboxStatus.SENT);
        assertThat(retained.getTerminalAt()).isEqualTo(NOW.minusDays(30));
    }

    private PushOutbox persistPending(String suffix, LocalDateTime dueAt) {
        Notification notification = notificationRepository.saveAndFlush(notification(suffix));
        return pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, dueAt));
    }

    private Notification notification(String suffix) {
        return Notification.builder()
                .recipientUserId(1101L)
                .actorUserId(1102L)
                .actorName("상태 행위자")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("상태 알림")
                .body("상태 본문")
                .sessionId(2101L)
                .participationId(3101L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey("push-outbox-state:" + suffix)
                .build();
    }
}
