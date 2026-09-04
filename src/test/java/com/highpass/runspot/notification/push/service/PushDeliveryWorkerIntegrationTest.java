package com.highpass.runspot.notification.push.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.config.FcmPushProperties;
import com.highpass.runspot.notification.push.domain.PushDeviceToken;
import com.highpass.runspot.notification.push.domain.PushPlatform;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import com.highpass.runspot.notification.push.outbox.PushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxFinalizeService;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxStatus;
import com.highpass.runspot.support.MySqlContainerSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "push.fcm.enabled=true",
        "push.fcm.batch-size=500",
        "push.fcm.publish-delay=1d",
        "push.fcm.jitter=0"
})
@Import(PushDeliveryWorkerIntegrationTest.DeliveryTestConfiguration.class)
class PushDeliveryWorkerIntegrationTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @MockitoBean
    private FirebaseApp firebaseApp;
    @MockitoBean
    private FirebaseMessaging firebaseMessaging;
    @Autowired
    private PushDeliveryWorker worker;
    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private MutableClock clock;
    @Autowired
    private FcmPushProperties fcmPushProperties;
    @Autowired
    private PushOutboxRepository pushOutboxRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PushDeviceTokenRepository pushDeviceTokenRepository;
    @Autowired
    private PushOutboxClaimService claimService;
    @Autowired
    private PushOutboxFinalizeService finalizeService;
    @Autowired
    private PushOutboxCleanupScheduler cleanupScheduler;
    @Autowired
    private PushDeviceTokenService pushDeviceTokenService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanData() {
        gateway.reset();
        clock.set(NOW);
        fcmPushProperties.setBatchSize(500);
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
        pushDeviceTokenRepository.deleteAll();
    }

    @Test
    void orderedMixedGatewayResultsFinalizeIndependentlyOutsideTransactions() {
        PushOutbox success = persistOutbox(1001L, "success", true, NOW);
        PushOutbox unregistered = persistOutbox(1002L, "unregistered", true, NOW);
        PushOutbox invalid = persistOutbox(1003L, "invalid", true, NOW);
        PushOutbox unavailable = persistOutbox(1004L, "unavailable", true, NOW);
        PushOutbox batchFailure = persistOutbox(1005L, "batch", true, NOW);
        gateway.respond(success.getNotification().getId(), PushSendResult.success(success.getNotification().getId()));
        gateway.respond(unregistered.getNotification().getId(), PushSendResult.terminal(
                unregistered.getNotification().getId(), "UNREGISTERED", true));
        gateway.respond(invalid.getNotification().getId(), PushSendResult.terminal(
                invalid.getNotification().getId(), "INVALID_ARGUMENT", false));
        gateway.respond(unavailable.getNotification().getId(), PushSendResult.retryable(
                unavailable.getNotification().getId(), "UNAVAILABLE"));
        gateway.respond(batchFailure.getNotification().getId(), PushSendResult.retryable(
                batchFailure.getNotification().getId(), "BATCH_FAILURE"));

        worker.deliverDuePushes();

        assertThat(status(success)).isEqualTo(PushOutboxStatus.SENT);
        assertThat(status(unregistered)).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(status(invalid)).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(status(unavailable)).isEqualTo(PushOutboxStatus.PENDING);
        assertThat(status(batchFailure)).isEqualTo(PushOutboxStatus.PENDING);
        assertThat(row(unavailable).getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(row(batchFailure).getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(pushDeviceTokenRepository.findByUserId(1002L)).isEmpty();
        assertThat(pushDeviceTokenRepository.findByUserId(1003L)).isPresent();
        assertThat(gateway.transactionStates).containsOnly(false);
        assertThat(gateway.sentNotificationIds()).containsExactly(List.of(
                success.getNotification().getId(),
                unregistered.getNotification().getId(),
                invalid.getNotification().getId(),
                unavailable.getNotification().getId(),
                batchFailure.getNotification().getId()
        ));
    }

    @Test
    void sendCompletionTimeControlsSentAndRetryTimestamps() {
        PushOutbox success = persistOutbox(1051L, "completion-success", true, NOW);
        PushOutbox retryable = persistOutbox(1052L, "completion-retry", true, NOW);
        gateway.respond(success.getNotification().getId(), PushSendResult.success(success.getNotification().getId()));
        gateway.respond(retryable.getNotification().getId(), PushSendResult.retryable(
                retryable.getNotification().getId(), "UNAVAILABLE"));
        gateway.beforeResults(messages -> clock.advance(Duration.ofSeconds(10)));

        worker.deliverDuePushes();

        assertThat(row(success).getSentAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(row(retryable).getNextAttemptAt()).isEqualTo(NOW.plusSeconds(15));
    }

    @Test
    void missingTokenFailsWithoutAttemptingGatewaySend() {
        PushOutbox outbox = persistOutbox(1101L, "missing-token", false, NOW);

        worker.deliverDuePushes();

        PushOutbox failed = row(outbox);
        assertThat(failed.getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isZero();
        assertThat(failed.getLastErrorCode()).isEqualTo("TOKEN_NOT_FOUND");
        assertThat(gateway.sentBatches).isEmpty();
    }

    @Test
    void fifthRetryableSendBecomesTerminalAndDoesNotReenterTheWorker() {
        PushOutbox outbox = persistOutbox(1201L, "exhausted", true, NOW);
        jdbcTemplate.update("UPDATE push_outbox SET attempts = 4 WHERE id = ?", outbox.getId());
        gateway.respond(outbox.getNotification().getId(), PushSendResult.retryable(
                outbox.getNotification().getId(), "UNAVAILABLE"));

        worker.deliverDuePushes();
        worker.deliverDuePushes();

        PushOutbox failed = row(outbox);
        assertThat(failed.getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(5);
        assertThat(failed.getLastErrorCode()).isEqualTo("UNAVAILABLE");
        assertThat(gateway.sentBatches).hasSize(1);
    }

    @Test
    void configuredClaimBatchIsPartitionedIntoGatewayBatchesOfAtMostOneHundred() {
        for (int index = 0; index < 101; index++) {
            persistOutbox(1300L + index, "partition-" + index, true, NOW);
        }

        worker.deliverDuePushes();

        assertThat(gateway.sentBatches).extracting(List::size).containsExactly(100, 1);
        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getStatus).containsOnly(PushOutboxStatus.SENT);
    }

    @Test
    void slowEarlierPartitionUsesFreshClockForLaterLeaseValidation() {
        List<PushOutbox> outboxes = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            outboxes.add(persistOutbox(1350L + index, "slow-partition-" + index, true, NOW));
        }
        AtomicBoolean advanced = new AtomicBoolean();
        gateway.beforeResults(messages -> {
            if (advanced.compareAndSet(false, true)) {
                clock.advance(fcmPushProperties.getLeaseDuration());
            }
        });

        worker.deliverDuePushes();

        assertThat(gateway.sentBatches).extracting(List::size).containsExactly(100);
        assertThat(status(outboxes.get(100))).isEqualTo(PushOutboxStatus.PROCESSING);
    }

    @Test
    @Timeout(60)
    void concurrentWorkersSendDisjointLiveLeases() throws Exception {
        fcmPushProperties.setBatchSize(2);
        for (int index = 0; index < 4; index++) {
            persistOutbox(1400L + index, "concurrent-" + index, true, NOW);
        }
        gateway.blockUntilBatchesArrive(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(worker::deliverDuePushes);
            Future<?> second = executor.submit(worker::deliverDuePushes);
            assertThat(gateway.awaitBlockedBatches()).isTrue();
            gateway.releaseBlockedBatches();
            first.get(10, SECONDS);
            second.get(10, SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }

        List<Long> sentIds = gateway.sentBatches.stream().flatMap(List::stream).toList();
        assertThat(sentIds).hasSize(4).doesNotHaveDuplicates();
        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getStatus).containsOnly(PushOutboxStatus.SENT);
    }

    @Test
    void expiredLeaseRecoversAfterAWorkerRestartAndAStaleFinalizerCannotOverwriteIt() {
        PushOutbox outbox = persistOutbox(1501L, "recovery", true, NOW);
        ClaimedPushOutbox stale = claimService.claim(1, NOW).get(0);
        clock.advance(fcmPushProperties.getLeaseDuration());

        worker.deliverDuePushes();

        assertThat(finalizeService.markSent(stale, NOW.plusSeconds(121))).isFalse();
        assertThat(row(outbox).getStatus()).isEqualTo(PushOutboxStatus.SENT);
        assertThat(row(outbox).getAttempts()).isEqualTo(1);
        assertThat(gateway.sentBatches).hasSize(1);
    }

    @Test
    void unregisteredCleanupCannotDeleteAReplacementTokenRegisteredAfterSend() {
        long userId = 1601L;
        PushOutbox outbox = persistOutbox(userId, "replacement", true, NOW);
        gateway.respond(outbox.getNotification().getId(), PushSendResult.terminal(
                outbox.getNotification().getId(), "UNREGISTERED", true));
        gateway.beforeResults(messages -> pushDeviceTokenService.upsert(userId, "replacement-token", PushPlatform.ANDROID));

        worker.deliverDuePushes();

        assertThat(row(outbox).getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(pushDeviceTokenRepository.findByUserId(userId).orElseThrow().getToken())
                .isEqualTo("replacement-token");
    }

    @Test
    void onlyUnregisteredCanDeleteTheExactFailedToken() {
        long userId = 1602L;
        PushOutbox outbox = persistOutbox(userId, "invalid-terminal", true, NOW);
        gateway.respond(outbox.getNotification().getId(), PushSendResult.terminal(
                outbox.getNotification().getId(), "INVALID_ARGUMENT", true));

        worker.deliverDuePushes();

        assertThat(row(outbox).getStatus()).isEqualTo(PushOutboxStatus.FAILED);
        assertThat(pushDeviceTokenRepository.findByUserId(userId)).isPresent();
    }

    @Test
    void terminalCleanupSchedulerUsesConfiguredRetentionAndPreservesPendingAndProcessingRows() {
        LocalDateTime old = NOW.minusDays(31);
        PushOutbox oldSent = persistOutbox(1701L, "old-sent", true, old);
        PushOutbox oldFailed = persistOutbox(1702L, "old-failed", true, old);
        PushOutbox pending = persistOutbox(1703L, "pending", true, NOW.plusDays(1));
        PushOutbox processing = persistOutbox(1704L, "processing", true, NOW);
        ClaimedPushOutbox sentClaim = claimService.claim(1, old).get(0);
        assertThat(finalizeService.markSent(sentClaim, old)).isTrue();
        ClaimedPushOutbox failedClaim = claimService.claim(1, old).get(0);
        assertThat(finalizeService.markFailed(failedClaim, old, "INVALID_ARGUMENT")).isTrue();
        claimService.claim(1, NOW);

        cleanupScheduler.cleanExpiredTerminalRows();

        assertThat(pushOutboxRepository.findById(oldSent.getId())).isEmpty();
        assertThat(pushOutboxRepository.findById(oldFailed.getId())).isEmpty();
        assertThat(status(pending)).isEqualTo(PushOutboxStatus.PENDING);
        assertThat(status(processing)).isEqualTo(PushOutboxStatus.PROCESSING);
    }

    private PushOutbox persistOutbox(long userId, String suffix, boolean withToken, LocalDateTime dueAt) {
        Notification notification = notificationRepository.saveAndFlush(notification(userId, suffix));
        if (withToken) {
            pushDeviceTokenRepository.saveAndFlush(PushDeviceToken.builder()
                    .userId(userId)
                    .token("token-" + suffix)
                    .platform(PushPlatform.ANDROID)
                    .build());
        }
        return pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, dueAt));
    }

    private Notification notification(long userId, String suffix) {
        return Notification.builder()
                .recipientUserId(userId)
                .actorUserId(999L)
                .actorName("푸시 행위자")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("푸시 제목")
                .body("푸시 본문")
                .sessionId(2000L + userId)
                .participationId(3000L + userId)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey("push-worker:" + suffix)
                .build();
    }

    private PushOutbox row(PushOutbox outbox) {
        return pushOutboxRepository.findById(outbox.getId()).orElseThrow();
    }

    private PushOutboxStatus status(PushOutbox outbox) {
        return row(outbox).getStatus();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeliveryTestConfiguration {

        @Bean
        @Primary
        MutableClock pushTestClock() {
            return new MutableClock(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }

        @Bean
        @Primary
        PushJitterSource deterministicPushJitterSource() {
            return () -> 0.5d;
        }

        @Bean
        @Primary
        RecordingGateway recordingGateway() {
            return new RecordingGateway();
        }
    }

    static class MutableClock extends Clock {

        private final ZoneId zone;
        private Instant instant;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        synchronized void set(LocalDateTime value) {
            instant = value.atZone(zone).toInstant();
        }

        synchronized void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public synchronized Instant instant() {
            return instant;
        }
    }

    static class RecordingGateway implements PushMessagingGateway {

        private final Map<Long, PushSendResult> results = new ConcurrentHashMap<>();
        private final List<List<Long>> sentBatches = new CopyOnWriteArrayList<>();
        private final List<Boolean> transactionStates = new CopyOnWriteArrayList<>();
        private Consumer<List<PushMessage>> beforeResults = ignored -> { };
        private CountDownLatch blockedBatches;
        private CountDownLatch releaseBlockedBatches;

        @Override
        public List<PushSendResult> send(List<PushMessage> messages) {
            transactionStates.add(isActualTransactionActive());
            sentBatches.add(messages.stream().map(PushMessage::notificationId).toList());
            awaitIfBlocked();
            beforeResults.accept(List.copyOf(messages));
            return messages.stream()
                    .map(message -> results.getOrDefault(message.notificationId(), PushSendResult.success(message.notificationId())))
                    .toList();
        }

        void respond(Long notificationId, PushSendResult result) {
            results.put(notificationId, result);
        }

        void beforeResults(Consumer<List<PushMessage>> callback) {
            beforeResults = callback;
        }

        void blockUntilBatchesArrive(int count) {
            blockedBatches = new CountDownLatch(count);
            releaseBlockedBatches = new CountDownLatch(1);
        }

        boolean awaitBlockedBatches() throws InterruptedException {
            return blockedBatches.await(10, SECONDS);
        }

        void releaseBlockedBatches() {
            releaseBlockedBatches.countDown();
        }

        List<List<Long>> sentNotificationIds() {
            return new ArrayList<>(sentBatches);
        }

        void reset() {
            results.clear();
            sentBatches.clear();
            transactionStates.clear();
            beforeResults = ignored -> { };
            blockedBatches = null;
            releaseBlockedBatches = null;
        }

        private void awaitIfBlocked() {
            if (blockedBatches == null) {
                return;
            }
            blockedBatches.countDown();
            try {
                if (!releaseBlockedBatches.await(10, SECONDS)) {
                    throw new IllegalStateException("동시 워커 전송 해제 대기 시간이 초과되었습니다.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("동시 워커 전송이 중단되었습니다.");
            }
        }
    }
}
