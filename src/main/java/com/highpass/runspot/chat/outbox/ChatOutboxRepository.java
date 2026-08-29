package com.highpass.runspot.chat.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatOutboxRepository extends JpaRepository<ChatOutbox, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select o from ChatOutbox o where o.status=:status and o.nextAttemptAt<=:now order by"
                + " o.id")
    List<ChatOutbox> findReady(
            @Param("status") OutboxStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
