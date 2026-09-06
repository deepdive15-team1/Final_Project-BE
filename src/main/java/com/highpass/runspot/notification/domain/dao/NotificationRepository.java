package com.highpass.runspot.notification.domain.dao;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId);

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId, Pageable pageable);

    List<Notification> findByRecipientUserIdAndIdLessThanOrderByIdDesc(
            Long recipientUserId,
            Long cursorId,
            Pageable pageable
    );

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    Optional<Notification> findByTypeAndParticipationIdAndRecipientUserIdAndActionStatus(
            NotificationType type,
            Long participationId,
            Long recipientUserId,
            NotificationActionStatus actionStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.id = :notificationId
              AND n.recipientUserId = :recipientUserId
              AND n.readAt IS NULL
            """)
    int markUnreadAsRead(
            @Param("notificationId") Long notificationId,
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") LocalDateTime readAt
    );

    boolean existsByIdAndRecipientUserId(Long notificationId, Long recipientUserId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.recipientUserId = :userId OR n.actorUserId = :userId")
    int deleteAllRelatedToUser(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.readAt = :readAt
            WHERE n.recipientUserId = :recipientUserId
              AND n.readAt IS NULL
            """)
    int markAllUnreadAsRead(
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") LocalDateTime readAt
    );
}
