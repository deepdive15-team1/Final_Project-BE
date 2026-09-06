package com.highpass.runspot.notification.push.outbox;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PushOutboxConcurrencyTest extends MySqlContainerSupport {

    private static final int WAIT_SECONDS = 10;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, SECONDS)).isTrue();
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    @Timeout(60)
    void concurrentClaimersReceiveDisjointIds() throws Exception {
        for (int index = 0; index < 4; index++) {
            persistPending("disjoint-" + index, NOW);
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<List<ClaimedPushOutbox>> first = executor.submit(() -> claimWhenStarted(2, ready, start));
        Future<List<ClaimedPushOutbox>> second = executor.submit(() -> claimWhenStarted(2, ready, start));
        assertThat(ready.await(WAIT_SECONDS, SECONDS)).isTrue();
        start.countDown();

        List<ClaimedPushOutbox> firstClaims = first.get(WAIT_SECONDS, SECONDS);
        List<ClaimedPushOutbox> secondClaims = second.get(WAIT_SECONDS, SECONDS);
        Set<Long> firstIds = ids(firstClaims);
        Set<Long> secondIds = ids(secondClaims);

        assertThat(firstClaims).hasSize(2);
        assertThat(secondClaims).hasSize(2);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        assertThat(firstIds).hasSize(2);
        assertThat(secondIds).hasSize(2);
        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getStatus)
                .containsOnly(PushOutboxStatus.PROCESSING);
    }

    @Test
    void liveLeaseCannotBeReclaimedButExpiredLeaseCan() {
        PushOutbox pending = persistPending("lease", NOW);
        ClaimedPushOutbox firstLease = claimService.claim(1, NOW).get(0);

        assertThat(claimService.claim(1, NOW.plusSeconds(119))).isEmpty();

        ClaimedPushOutbox recovered = claimService.claim(1, NOW.plusSeconds(120)).get(0);
        assertThat(recovered.outboxId()).isEqualTo(pending.getId());
        assertThat(recovered.leaseToken()).isNotEqualTo(firstLease.leaseToken());
    }

    @Test
    void expiredProcessingLeaseIsClaimedEvenWhenNextAttemptWasAdvanced() {
        PushOutbox pending = persistPending("lease-query", NOW);
        claimService.claim(1, NOW);
        jdbcTemplate.update(
                "UPDATE push_outbox SET next_attempt_at = ? WHERE id = ?",
                NOW.plusHours(1),
                pending.getId()
        );

        List<ClaimedPushOutbox> recovered = claimService.claim(1, NOW.plusSeconds(120));

        assertThat(recovered).singleElement().extracting(ClaimedPushOutbox::outboxId)
                .isEqualTo(pending.getId());
    }

    @Test
    void staleLeaseCannotFinalizeReclaimedRow() {
        persistPending("stale", NOW);
        ClaimedPushOutbox stale = claimService.claim(1, NOW).get(0);
        ClaimedPushOutbox current = claimService.claim(1, NOW.plusSeconds(120)).get(0);

        assertThat(finalizeService.markSent(stale, NOW.plusSeconds(121))).isFalse();
        assertThat(finalizeService.markFailed(stale, NOW.plusSeconds(121), "INVALID_ARGUMENT")).isFalse();
        assertThat(finalizeService.markFailedWithoutAttempt(stale, NOW.plusSeconds(121), "TOKEN_NOT_FOUND"))
                .isFalse();
        assertThat(finalizeService.reschedule(stale, NOW.plusMinutes(5), "UNAVAILABLE")).isFalse();

        PushOutbox processing = pushOutboxRepository.findById(current.outboxId()).orElseThrow();
        assertThat(processing.getStatus()).isEqualTo(PushOutboxStatus.PROCESSING);
        assertThat(processing.getLeaseToken()).isEqualTo(current.leaseToken().toString());
        assertThat(processing.getAttempts()).isZero();
        assertThat(finalizeService.markSent(current, NOW.plusSeconds(122))).isTrue();
        assertThat(claimService.claim(1, NOW.plusDays(1))).isEmpty();
    }

    @Test
    @Timeout(60)
    void manualQaProbeReportsIndexClaimsCasAndRetentionObservables() throws Exception {
        for (int index = 0; index < 4; index++) {
            persistPending("manual-" + index, NOW);
        }
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<List<ClaimedPushOutbox>> first = executor.submit(() -> claimWhenStarted(2, ready, start));
        Future<List<ClaimedPushOutbox>> second = executor.submit(() -> claimWhenStarted(2, ready, start));
        assertThat(ready.await(WAIT_SECONDS, SECONDS)).isTrue();
        start.countDown();
        List<ClaimedPushOutbox> firstClaims = first.get(WAIT_SECONDS, SECONDS);
        List<ClaimedPushOutbox> secondClaims = second.get(WAIT_SECONDS, SECONDS);
        ClaimedPushOutbox stale = Stream.concat(firstClaims.stream(), secondClaims.stream())
                .min(java.util.Comparator.comparing(ClaimedPushOutbox::outboxId))
                .orElseThrow();
        ClaimedPushOutbox reclaimed = claimService.claim(1, NOW.plusSeconds(120)).get(0);
        boolean staleFinalize = finalizeService.markSent(stale, NOW.plusSeconds(121));
        int deleted = cleanupService.deleteExpiredTerminalRows(NOW.plusDays(31));

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList("SHOW INDEX FROM push_outbox");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status, attempts, lease_token, lease_until, terminal_at FROM push_outbox ORDER BY id"
        );
        System.out.printf(
                "MANUAL_QA indexes=%s firstIds=%s secondIds=%s reclaimedId=%d staleFinalize=%s "
                        + "cleanupDeleted=%d rows=%s%n",
                indexes.stream().map(row -> row.get("Key_name") + ":" + row.get("Column_name")).toList(),
                ids(firstClaims),
                ids(secondClaims),
                reclaimed.outboxId(),
                staleFinalize,
                deleted,
                rows
        );

        assertThat(ids(firstClaims)).doesNotContainAnyElementsOf(ids(secondClaims));
        assertThat(reclaimed.outboxId()).isEqualTo(stale.outboxId());
        assertThat(staleFinalize).isFalse();
        assertThat(deleted).isZero();
        assertThat(rows).hasSize(4);
        assertThat(indexes).anySatisfy(row -> assertThat(row.get("Key_name")).isEqualTo("idx_push_outbox_claim"));
    }

    private List<ClaimedPushOutbox> claimWhenStarted(
            int batchSize,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(WAIT_SECONDS, SECONDS)) {
            throw new IllegalStateException("동시 클레임 시작 신호 대기 시간이 초과되었습니다.");
        }
        return claimService.claim(batchSize, NOW);
    }

    private Set<Long> ids(List<ClaimedPushOutbox> claims) {
        return claims.stream()
                .map(ClaimedPushOutbox::outboxId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private PushOutbox persistPending(String suffix, LocalDateTime dueAt) {
        Notification notification = notificationRepository.saveAndFlush(notification(suffix));
        return pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, dueAt));
    }

    private Notification notification(String suffix) {
        return Notification.builder()
                .recipientUserId(1201L)
                .actorUserId(1202L)
                .actorName("동시성 행위자")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("동시성 알림")
                .body("동시성 본문")
                .sessionId(2201L)
                .participationId(3201L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey("push-outbox-concurrency:" + suffix)
                .build();
    }
}
