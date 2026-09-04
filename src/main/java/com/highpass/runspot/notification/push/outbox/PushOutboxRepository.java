package com.highpass.runspot.notification.push.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushOutboxRepository extends JpaRepository<PushOutbox, Long> {

    @Query(value = """
            SELECT *
            FROM push_outbox
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'PROCESSING' AND lease_until <= :now)
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PushOutbox> lockClaimable(
            @Param("now") LocalDateTime now,
            @Param("batchSize") int batchSize
    );

    Optional<PushOutbox> findByIdAndStatusAndLeaseTokenAndLeaseUntilAfter(
            Long id,
            PushOutboxStatus status,
            String leaseToken,
            LocalDateTime now
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE push_outbox
            SET status = 'SENT',
                attempts = attempts + 1,
                sent_at = :sentAt,
                terminal_at = :sentAt,
                last_error_code = NULL,
                lease_until = NULL,
                lease_token = NULL
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
            """, nativeQuery = true)
    int markSent(
            @Param("outboxId") Long outboxId,
            @Param("leaseToken") String leaseToken,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE push_outbox
            SET status = 'FAILED',
                attempts = attempts + 1,
                terminal_at = :terminalAt,
                last_error_code = :errorCode,
                lease_until = NULL,
                lease_token = NULL
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
            """, nativeQuery = true)
    int markFailed(
            @Param("outboxId") Long outboxId,
            @Param("leaseToken") String leaseToken,
            @Param("terminalAt") LocalDateTime terminalAt,
            @Param("errorCode") String errorCode
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE push_outbox
            SET status = 'FAILED',
                terminal_at = :terminalAt,
                last_error_code = :errorCode,
                lease_until = NULL,
                lease_token = NULL
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
            """, nativeQuery = true)
    int markFailedWithoutAttempt(
            @Param("outboxId") Long outboxId,
            @Param("leaseToken") String leaseToken,
            @Param("terminalAt") LocalDateTime terminalAt,
            @Param("errorCode") String errorCode
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE push_outbox
            SET status = 'PENDING',
                attempts = attempts + 1,
                next_attempt_at = :nextAttemptAt,
                last_error_code = :errorCode,
                lease_until = NULL,
                lease_token = NULL
            WHERE id = :outboxId
              AND status = 'PROCESSING'
              AND lease_token = :leaseToken
            """, nativeQuery = true)
    int reschedule(
            @Param("outboxId") Long outboxId,
            @Param("leaseToken") String leaseToken,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("errorCode") String errorCode
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM push_outbox
            WHERE status IN ('SENT', 'FAILED')
              AND terminal_at < :cutoff
            """, nativeQuery = true)
    int deleteTerminalBefore(@Param("cutoff") LocalDateTime cutoff);
}
