package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.push.config.FcmPushProperties;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.gateway.PushMessagingGateway;
import com.highpass.runspot.notification.push.gateway.PushSendResult;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxFinalizeService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class PushDeliveryWorker {

    private static final int GATEWAY_BATCH_SIZE = 100;

    private final FcmPushProperties fcmPushProperties;
    private final PushOutboxClaimService claimService;
    private final PushDeliveryPreparationService preparationService;
    private final PushOutboxFinalizeService finalizeService;
    private final PushMessagingGateway pushMessagingGateway;
    private final PushBackoffPolicy pushBackoffPolicy;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${push.fcm.publish-delay}")
    public void deliverDuePushes() {
        List<ClaimedPushOutbox> claims = claimService.claim(
                fcmPushProperties.getBatchSize(),
                LocalDateTime.now(clock)
        );
        for (int start = 0; start < claims.size(); start += GATEWAY_BATCH_SIZE) {
            deliverPartition(claims.subList(start, Math.min(start + GATEWAY_BATCH_SIZE, claims.size())));
        }
    }

    private void deliverPartition(List<ClaimedPushOutbox> claims) {
        List<PushDeliveryPreparation> readyDeliveries = new ArrayList<>();
        for (ClaimedPushOutbox claim : claims) {
            processPreparation(preparationService.prepare(claim, LocalDateTime.now(clock)), readyDeliveries);
        }
        if (readyDeliveries.isEmpty()) {
            return;
        }

        List<PushMessage> messages = readyDeliveries.stream().map(PushDeliveryPreparation::message).toList();
        List<PushSendResult> results = pushMessagingGateway.send(messages);
        LocalDateTime sendCompletedAt = LocalDateTime.now(clock);
        if (results.size() != readyDeliveries.size()) {
            throw new IllegalStateException("FCM 게이트웨이 결과 수가 요청 수와 다릅니다.");
        }
        for (int index = 0; index < readyDeliveries.size(); index++) {
            finalizeResult(readyDeliveries.get(index), results.get(index), sendCompletedAt);
        }
    }

    private void processPreparation(
            PushDeliveryPreparation preparation,
            List<PushDeliveryPreparation> readyDeliveries
    ) {
        switch (preparation.kind()) {
            case READY -> readyDeliveries.add(preparation);
            case TOKEN_NOT_FOUND -> finalizeService.markFailedWithoutAttempt(
                    preparation.claim(),
                    LocalDateTime.now(clock),
                    "TOKEN_NOT_FOUND"
            );
            case LEASE_LOST -> { }
        }
    }

    private void finalizeResult(PushDeliveryPreparation delivery, PushSendResult result, LocalDateTime sendCompletedAt) {
        PushMessage message = delivery.message();
        if (message.notificationId() != result.notificationId()) {
            throw new IllegalStateException("FCM 게이트웨이 결과 알림 ID가 요청과 다릅니다.");
        }
        ClaimedPushOutbox claim = delivery.claim();
        switch (result.failureKind()) {
            case SUCCESS -> finalizeService.markSent(claim, sendCompletedAt);
            case TERMINAL -> finalizeTerminal(claim, message, result, sendCompletedAt);
            case RETRYABLE -> finalizeRetryable(claim, result, sendCompletedAt);
        }
    }

    private void finalizeTerminal(
            ClaimedPushOutbox claim,
            PushMessage message,
            PushSendResult result,
            LocalDateTime sendCompletedAt
    ) {
        log.error("FCM terminal push failure: outboxId={}, notificationId={}, attempt={}, errorCode={}",
                claim.outboxId(), claim.notificationId(), claim.attemptNumber(), result.errorCode());
        finalizeService.markFailedAndDeleteMatchingToken(
                claim,
                sendCompletedAt,
                result.errorCode(),
                message.token(),
                result.deleteToken()
        );
    }

    private void finalizeRetryable(
            ClaimedPushOutbox claim,
            PushSendResult result,
            LocalDateTime sendCompletedAt
    ) {
        if (pushBackoffPolicy.isExhausted(claim.attemptNumber())) {
            finalizeService.markFailed(claim, sendCompletedAt, result.errorCode());
            return;
        }
        finalizeService.reschedule(
                claim,
                sendCompletedAt.plus(pushBackoffPolicy.delayForFailedAttempt(claim.attemptNumber())),
                result.errorCode()
        );
    }
}
