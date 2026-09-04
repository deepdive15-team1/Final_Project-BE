package com.highpass.runspot.notification.push.outbox;

import com.highpass.runspot.notification.push.config.FcmPushProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims work in an independent, short transaction. The returned values are detached immutable
 * descriptors; callers must perform all network work after this method has committed.
 */
@Service
@RequiredArgsConstructor
public class PushOutboxClaimService {

    private final PushOutboxRepository pushOutboxRepository;
    private final FcmPushProperties fcmPushProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedPushOutbox> claim(int batchSize, LocalDateTime now) {
        if (batchSize < 1 || batchSize > fcmPushProperties.getBatchSize()) {
            throw new IllegalArgumentException("푸시 아웃박스 배치 크기는 1 이상 500 이하여야 합니다.");
        }

        LocalDateTime leaseUntil = now.plus(fcmPushProperties.getLeaseDuration());
        return pushOutboxRepository.lockClaimable(now, batchSize).stream()
                .map(outbox -> claim(outbox, now, leaseUntil))
                .toList();
    }

    private ClaimedPushOutbox claim(
            PushOutbox outbox,
            LocalDateTime now,
            LocalDateTime leaseUntil
    ) {
        UUID leaseToken = UUID.randomUUID();
        outbox.claim(leaseToken, now, leaseUntil);
        return new ClaimedPushOutbox(
                outbox.getId(),
                outbox.getNotification().getId(),
                outbox.getRecipientUserId(),
                outbox.getAttempts() + 1,
                leaseToken,
                leaseUntil
        );
    }

    public record ClaimedPushOutbox(
            Long outboxId,
            Long notificationId,
            Long recipientUserId,
            int attemptNumber,
            UUID leaseToken,
            LocalDateTime leaseUntil
    ) {
    }
}
