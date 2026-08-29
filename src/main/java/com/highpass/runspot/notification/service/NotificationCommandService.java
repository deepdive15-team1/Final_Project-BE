package com.highpass.runspot.notification.service;

import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.exception.NotificationErrorCode;
import com.highpass.runspot.notification.exception.NotificationException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public void markAsRead(Long notificationId, Long recipientUserId) {
        LocalDateTime readAt = LocalDateTime.now(clock);
        int updatedCount = notificationRepository.markUnreadAsRead(notificationId, recipientUserId, readAt);
        if (updatedCount == 0 && !notificationRepository.existsByIdAndRecipientUserId(notificationId, recipientUserId)) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    public void markAllAsRead(Long recipientUserId) {
        LocalDateTime readAt = LocalDateTime.now(clock);
        notificationRepository.markAllUnreadAsRead(recipientUserId, readAt);
    }
}
