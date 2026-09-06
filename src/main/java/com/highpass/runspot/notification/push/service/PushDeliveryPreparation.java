package com.highpass.runspot.notification.push.service;

import com.highpass.runspot.notification.push.gateway.PushMessage;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import java.util.Objects;

public record PushDeliveryPreparation(ClaimedPushOutbox claim, Kind kind, PushMessage message) {

    public enum Kind {
        READY,
        TOKEN_NOT_FOUND,
        LEASE_LOST
    }

    public PushDeliveryPreparation {
        Objects.requireNonNull(claim, "claim must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        if ((kind == Kind.READY) != (message != null)) {
            throw new IllegalArgumentException("준비 상태와 푸시 메시지 조합이 올바르지 않습니다.");
        }
    }

    public static PushDeliveryPreparation ready(ClaimedPushOutbox claim, PushMessage message) {
        return new PushDeliveryPreparation(claim, Kind.READY, message);
    }

    public static PushDeliveryPreparation tokenNotFound(ClaimedPushOutbox claim) {
        return new PushDeliveryPreparation(claim, Kind.TOKEN_NOT_FOUND, null);
    }

    public static PushDeliveryPreparation leaseLost(ClaimedPushOutbox claim) {
        return new PushDeliveryPreparation(claim, Kind.LEASE_LOST, null);
    }
}
