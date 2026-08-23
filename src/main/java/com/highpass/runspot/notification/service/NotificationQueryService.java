package com.highpass.runspot.notification.service;

import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.service.dto.response.NotificationActorResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationFeedResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationUnreadCountResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public NotificationFeedResponse getNotificationFeed(Long recipientUserId, Long cursorId, int size) {
        validatePageRequest(cursorId, size);

        Pageable pageable = PageRequest.of(0, size + 1);
        List<Notification> notifications = new ArrayList<>(cursorId == null
                ? notificationRepository.findByRecipientUserIdOrderByIdDesc(recipientUserId, pageable)
                : notificationRepository.findByRecipientUserIdAndIdLessThanOrderByIdDesc(recipientUserId, cursorId, pageable));

        boolean hasNext = notifications.size() > size;
        if (hasNext) {
            notifications.remove(size);
        }

        Long nextCursorId = notifications.isEmpty() ? null : notifications.get(notifications.size() - 1).getId();
        List<NotificationResponse> responses = notifications.stream()
                .map(this::toResponse)
                .toList();

        return new NotificationFeedResponse(responses, nextCursorId, hasNext);
    }

    public NotificationUnreadCountResponse getUnreadCount(Long recipientUserId) {
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
        return new NotificationUnreadCountResponse(unreadCount);
    }

    private void validatePageRequest(Long cursorId, int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100.");
        }
        if (cursorId != null && cursorId <= 0) {
            throw new IllegalArgumentException("cursorId must be positive.");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationActorResponse actor = notification.getActorUserId() == null
                ? null
                : new NotificationActorResponse(
                        notification.getActorUserId(),
                        notification.getActorName(),
                        notification.getActorProfileImageUrl()
                );

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                actor,
                notification.getSessionId(),
                notification.getParticipationId(),
                notification.getActionType(),
                notification.getActionStatus(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
