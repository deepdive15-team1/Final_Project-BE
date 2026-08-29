package com.highpass.runspot.chat.outbox;

import com.highpass.runspot.common.domain.BaseTimeEntity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_outbox",
        indexes =
                @Index(name = "idx_chat_outbox_publish", columnList = "status,next_attempt_at,id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatOutbox extends BaseTimeEntity {
    private static final long MAX_BACKOFF_SECONDS = 300;
    private static final int MAX_BACKOFF_SHIFT = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 200)
    private String destination;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    public static ChatOutbox pending(Long aggregateId, String destination, String payload) {
        ChatOutbox outbox = new ChatOutbox();
        outbox.aggregateId = aggregateId;
        outbox.destination = destination;
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.nextAttemptAt = LocalDateTime.now();
        return outbox;
    }

    public void published() {
        status = OutboxStatus.PUBLISHED;
        publishedAt = LocalDateTime.now();
    }

    public void failed() {
        attempts++;
        long backoffSeconds = Math.min(MAX_BACKOFF_SECONDS, 1L << Math.min(attempts, MAX_BACKOFF_SHIFT));
        nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds);
    }
}
