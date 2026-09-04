package com.highpass.runspot.notification.service;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.service.PushOutboxEnqueuer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationReminderCreator {

    private final NotificationRepository notificationRepository;
    private final PushOutboxEnqueuer pushOutboxEnqueuer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createReminder(Long sessionId, String sessionTitle, Long recipientUserId) {
        Notification reminder = notificationRepository.saveAndFlush(Notification.builder()
                .recipientUserId(recipientUserId)
                .type(NotificationType.SESSION_START_REMINDER)
                .title("러닝 시작 30분 전")
                .body("스트레칭을 잊지 마세요! [" + sessionTitle + "] 러닝이 곧 시작됩니다.")
                .sessionId(sessionId)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey("SESSION_START_REMINDER:" + sessionId + ":" + recipientUserId)
                .build());
        pushOutboxEnqueuer.enqueue(reminder);
    }
}
