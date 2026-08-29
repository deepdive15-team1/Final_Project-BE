package com.highpass.runspot.notification.api;

import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.notification.service.NotificationCommandService;
import com.highpass.runspot.notification.service.NotificationQueryService;
import com.highpass.runspot.notification.service.dto.response.NotificationFeedResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationUnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림")
@RestController
@RequestMapping("/users/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    @Operation(summary = "내 알림 목록 조회")
    @ApiResponse(responseCode = "200", description = "알림 목록 조회 성공")
    @GetMapping
    public ResponseEntity<NotificationFeedResponse> getNotifications(
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        requireAuthenticated(userPrincipal);
        return ResponseEntity.ok(notificationQueryService.getNotificationFeed(userPrincipal.getId(), cursorId, size));
    }

    @Operation(summary = "읽지 않은 알림 개수 조회")
    @ApiResponse(responseCode = "200", description = "읽지 않은 알림 개수 조회 성공")
    @GetMapping("/unread-count")
    public ResponseEntity<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        requireAuthenticated(userPrincipal);
        return ResponseEntity.ok(notificationQueryService.getUnreadCount(userPrincipal.getId()));
    }

    @Operation(summary = "알림 읽음 처리")
    @ApiResponse(responseCode = "204", description = "알림 읽음 처리 성공")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        requireAuthenticated(userPrincipal);
        notificationCommandService.markAsRead(notificationId, userPrincipal.getId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "모든 알림 읽음 처리")
    @ApiResponse(responseCode = "204", description = "모든 알림 읽음 처리 성공")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        requireAuthenticated(userPrincipal);
        notificationCommandService.markAllAsRead(userPrincipal.getId());
        return ResponseEntity.noContent().build();
    }

    private void requireAuthenticated(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
    }
}
