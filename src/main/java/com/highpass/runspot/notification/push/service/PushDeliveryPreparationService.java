package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.outbox.PushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeliveryPreparationService {

    private final PushOutboxRepository pushOutboxRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public PushDeliveryPreparation prepare(ClaimedPushOutbox claim, LocalDateTime now) {
        PushOutbox outbox = pushOutboxRepository.findByIdAndStatusAndLeaseTokenAndLeaseUntilAfter(
                claim.outboxId(),
                PushOutboxStatus.PROCESSING,
                claim.leaseToken().toString(),
                now
        ).orElse(null);
        if (outbox == null) {
            return PushDeliveryPreparation.leaseLost(claim);
        }
        return pushDeviceTokenRepository.findByUserId(claim.recipientUserId())
                .map(token -> PushDeliveryPreparation.ready(claim, PushMessage.from(outbox.getNotification(), token.getToken())))
                .orElseGet(() -> PushDeliveryPreparation.tokenNotFound(claim));
    }
}
