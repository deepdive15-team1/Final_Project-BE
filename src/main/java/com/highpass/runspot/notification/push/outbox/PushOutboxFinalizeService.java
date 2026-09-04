package com.highpass.runspot.notification.push.outbox;

import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PushOutboxFinalizeService {

    private final PushOutboxRepository pushOutboxRepository;

    public boolean markSent(ClaimedPushOutbox claim, LocalDateTime sentAt) {
        return pushOutboxRepository.markSent(
                claim.outboxId(),
                claim.leaseToken().toString(),
                sentAt
        ) == 1;
    }

    public boolean markFailed(
            ClaimedPushOutbox claim,
            LocalDateTime terminalAt,
            String errorCode
    ) {
        return pushOutboxRepository.markFailed(
                claim.outboxId(),
                claim.leaseToken().toString(),
                terminalAt,
                errorCode
        ) == 1;
    }

    public boolean markFailedWithoutAttempt(
            ClaimedPushOutbox claim,
            LocalDateTime terminalAt,
            String errorCode
    ) {
        return pushOutboxRepository.markFailedWithoutAttempt(
                claim.outboxId(),
                claim.leaseToken().toString(),
                terminalAt,
                errorCode
        ) == 1;
    }

    public boolean reschedule(
            ClaimedPushOutbox claim,
            LocalDateTime nextAttemptAt,
            String errorCode
    ) {
        return pushOutboxRepository.reschedule(
                claim.outboxId(),
                claim.leaseToken().toString(),
                nextAttemptAt,
                errorCode
        ) == 1;
    }
}
