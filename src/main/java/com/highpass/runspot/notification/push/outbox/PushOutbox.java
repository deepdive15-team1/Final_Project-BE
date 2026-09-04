package com.highpass.runspot.notification.push.outbox;

import com.highpass.runspot.common.domain.BaseTimeEntity;
import com.highpass.runspot.notification.domain.Notification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Durable push delivery work. Delivery is at-least-once: a process failure after an external
 * provider accepts a message but before finalization may cause the same notification ID to be
 * delivered again. This model does not provide exactly-once delivery.
 */
@Getter
@Entity
@Table(
        name = "push_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_outbox_notification_id",
                columnNames = "notification_id"
        ),
        indexes = @Index(
                name = "idx_push_outbox_claim",
                columnList = "status,next_attempt_at,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushOutbox extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, updatable = false)
    private Notification notification;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushOutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "lease_token", length = 36)
    private String leaseToken;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "terminal_at")
    private LocalDateTime terminalAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    public static PushOutbox pending(Notification notification, LocalDateTime nextAttemptAt) {
        PushOutbox outbox = new PushOutbox();
        outbox.notification = notification;
        outbox.recipientUserId = notification.getRecipientUserId();
        outbox.status = PushOutboxStatus.PENDING;
        outbox.nextAttemptAt = nextAttemptAt;
        return outbox;
    }

    boolean isClaimable(LocalDateTime now) {
        return switch (status) {
            case PENDING -> !nextAttemptAt.isAfter(now);
            case PROCESSING -> leaseUntil != null && !leaseUntil.isAfter(now);
            case SENT, FAILED -> false;
        };
    }

    void claim(UUID newLeaseToken, LocalDateTime now, LocalDateTime newLeaseUntil) {
        if (!isClaimable(now)) {
            throw new IllegalStateException("클레임할 수 없는 푸시 아웃박스 상태입니다.");
        }
        status = PushOutboxStatus.PROCESSING;
        leaseToken = newLeaseToken.toString();
        leaseUntil = newLeaseUntil;
        nextAttemptAt = newLeaseUntil;
    }
}
