package com.highpass.runspot.notification.domain.dao;

import com.highpass.runspot.notification.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId);

    List<Notification> findByRecipientUserIdOrderByIdDesc(Long recipientUserId, Pageable pageable);

    List<Notification> findByRecipientUserIdAndIdLessThanOrderByIdDesc(
            Long recipientUserId,
            Long cursorId,
            Pageable pageable
    );

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);
}
