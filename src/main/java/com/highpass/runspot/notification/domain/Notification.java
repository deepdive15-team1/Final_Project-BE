package com.highpass.runspot.notification.domain;

import com.highpass.runspot.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notifications_deduplication_key",
                        columnNames = "deduplication_key"
                )
        },
        indexes = {
                @Index(name = "idx_notifications_recipient_user_id_id", columnList = "recipient_user_id,id"),
                @Index(name = "idx_notifications_recipient_user_id_read_at", columnList = "recipient_user_id,read_at")
        }
)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private Long recipientUserId;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_name", length = 30, updatable = false)
    private String actorName;

    @Column(name = "actor_profile_image_url", length = 512, updatable = false)
    private String actorProfileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 100, updatable = false)
    private String title;

    @Column(nullable = false, length = 500, updatable = false)
    private String body;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "participation_id", updatable = false)
    private Long participationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20, updatable = false)
    private NotificationActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_status", nullable = false, length = 20)
    @Builder.Default
    private NotificationActionStatus actionStatus = NotificationActionStatus.NONE;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "deduplication_key", nullable = false, length = 120, updatable = false)
    private String deduplicationKey;

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public void resolveAction() {
        if (actionStatus == NotificationActionStatus.PENDING) {
            actionStatus = NotificationActionStatus.RESOLVED;
        }
    }
}
