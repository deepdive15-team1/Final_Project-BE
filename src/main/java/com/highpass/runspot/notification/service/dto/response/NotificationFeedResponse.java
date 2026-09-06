package com.highpass.runspot.notification.service.dto.response;

import java.util.List;

public record NotificationFeedResponse(
        List<NotificationResponse> notifications,
        Long nextCursorId,
        boolean hasNext
) {
}
