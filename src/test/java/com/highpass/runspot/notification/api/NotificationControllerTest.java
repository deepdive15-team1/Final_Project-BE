package com.highpass.runspot.notification.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.highpass.runspot.common.config.SecurityConfig;
import com.highpass.runspot.common.exception.handler.ApiExceptionHandler;
import com.highpass.runspot.common.jwt.JwtAuthenticationFilter;
import com.highpass.runspot.common.security.UserPrincipal;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.exception.NotificationErrorCode;
import com.highpass.runspot.notification.exception.NotificationException;
import com.highpass.runspot.notification.service.NotificationCommandService;
import com.highpass.runspot.notification.service.NotificationQueryService;
import com.highpass.runspot.notification.service.dto.response.NotificationActorResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationFeedResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationResponse;
import com.highpass.runspot.notification.service.dto.response.NotificationUnreadCountResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, SecurityConfig.class})
class NotificationControllerTest {

    private static final Long USER_ID = 10L;
    private static final String BASE_URL = "/users/me/notifications";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryService notificationQueryService;

    @MockitoBean
    private NotificationCommandService notificationCommandService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void authenticate() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new UserPrincipal(USER_ID), null));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getNotificationsReturnsFeedWithDefaultSize() throws Exception {
        given(notificationQueryService.getNotificationFeed(USER_ID, null, 20))
                .willReturn(new NotificationFeedResponse(List.of(notification()), 100L, true));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].id").value(101))
                .andExpect(jsonPath("$.notifications[0].actor.name").value("러너"))
                .andExpect(jsonPath("$.nextCursorId").value(100))
                .andExpect(jsonPath("$.hasNext").value(true));

        then(notificationQueryService).should().getNotificationFeed(USER_ID, null, 20);
    }

    @Test
    void getUnreadCountReturnsCount() throws Exception {
        given(notificationQueryService.getUnreadCount(USER_ID))
                .willReturn(new NotificationUnreadCountResponse(2L));

        mockMvc.perform(get(BASE_URL + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        then(notificationQueryService).should().getUnreadCount(USER_ID);
    }

    @Test
    void markAsReadReturnsNoContent() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/101/read"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(notificationCommandService).should().markAsRead(101L, USER_ID);
    }

    @Test
    void markAllAsReadReturnsNoContent() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/read-all"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(notificationCommandService).should().markAllAsRead(USER_ID);
    }

    @Test
    void unauthenticatedRequestsReturnUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get(BASE_URL + "/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(patch(BASE_URL + "/101/read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(patch(BASE_URL + "/read-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void getNotificationsReturnsBadRequestForInvalidCursorOrSize() throws Exception {
        given(notificationQueryService.getNotificationFeed(USER_ID, null, 0))
                .willThrow(new IllegalArgumentException("size must be between 1 and 100."));
        given(notificationQueryService.getNotificationFeed(USER_ID, null, 101))
                .willThrow(new IllegalArgumentException("size must be between 1 and 100."));
        given(notificationQueryService.getNotificationFeed(USER_ID, 0L, 20))
                .willThrow(new IllegalArgumentException("cursorId must be positive."));
        given(notificationQueryService.getNotificationFeed(USER_ID, -1L, 20))
                .willThrow(new IllegalArgumentException("cursorId must be positive."));

        mockMvc.perform(get(BASE_URL).param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE_URL).param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE_URL).param("cursorId", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE_URL).param("cursorId", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void markAsReadReturnsNotFoundForForeignOrMissingNotification() throws Exception {
        willThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationCommandService).markAsRead(200L, USER_ID);
        willThrow(new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))
                .given(notificationCommandService).markAsRead(999L, USER_ID);

        mockMvc.perform(patch(BASE_URL + "/200/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(patch(BASE_URL + "/999/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private NotificationResponse notification() {
        return new NotificationResponse(
                101L,
                NotificationType.PARTICIPATION_REQUESTED,
                "새로운 러너가 대기 중이에요!",
                "러너님이 [한강 러닝]에 참여를 신청했습니다.",
                new NotificationActorResponse(20L, "러너", null),
                30L,
                40L,
                NotificationActionType.APPROVE_OR_REJECT,
                NotificationActionStatus.PENDING,
                false,
                null,
                LocalDateTime.of(2026, 8, 30, 9, 0)
        );
    }
}
