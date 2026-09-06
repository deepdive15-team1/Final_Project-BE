package com.highpass.runspot.auth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.RefreshToken;
import com.highpass.runspot.auth.domain.dao.RefreshTokenRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import com.highpass.runspot.common.jwt.JwtProvider;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.service.PushDeviceTokenService;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRunningStatsRepository userRunningStatsRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PushOutboxRepository pushOutboxRepository;
    @Mock private PushDeviceTokenService pushDeviceTokenService;
    @Mock private RatingRepository ratingRepository;
    @Mock private SessionParticipantRepository sessionParticipantRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                userRunningStatsRepository,
                notificationRepository,
                pushOutboxRepository,
                pushDeviceTokenService,
                ratingRepository,
                sessionParticipantRepository,
                sessionRepository,
                jwtProvider,
                passwordEncoder
        );
    }

    @Test
    void withdrawDeletesReferencedDataBeforeUser() {
        Long userId = 33L;
        when(userRepository.existsById(userId)).thenReturn(true);

        authService.withdraw(userId);

        InOrder order = inOrder(
                pushOutboxRepository,
                pushDeviceTokenService,
                notificationRepository,
                ratingRepository,
                sessionParticipantRepository,
                sessionRepository,
                userRunningStatsRepository,
                refreshTokenRepository,
                userRepository
        );
        order.verify(pushOutboxRepository).deleteAllRelatedToUserNotifications(userId);
        order.verify(pushDeviceTokenService).delete(userId);
        order.verify(notificationRepository).deleteAllRelatedToUser(userId);
        order.verify(ratingRepository).deleteAllRelatedToUser(userId);
        order.verify(sessionParticipantRepository).deleteAllRelatedToUser(userId);
        order.verify(sessionRepository).deleteByHostUserId(userId);
        order.verify(userRunningStatsRepository).deleteByUserId(userId);
        order.verify(refreshTokenRepository).deleteByUserId(userId);
        order.verify(userRepository).deleteById(userId);
    }

    @Test
    void withdrawRejectsUnknownUserWithoutDeletingAnything() {
        Long userId = 999L;
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.withdraw(userId));

        verify(notificationRepository, never()).deleteAllRelatedToUser(userId);
        verify(pushOutboxRepository, never()).deleteAllRelatedToUserNotifications(userId);
        verify(pushDeviceTokenService, never()).delete(userId);
        verify(ratingRepository, never()).deleteAllRelatedToUser(userId);
        verify(userRepository, never()).deleteById(userId);
    }

    @Test
    void logoutCleansTheStoredRefreshTokenOwnerBeforeDeletingThatRefreshToken() {
        Long storedOwnerId = 44L;
        String refreshToken = "stored-refresh-token";
        when(refreshTokenRepository.findByToken(refreshToken)).thenReturn(Optional.of(
                RefreshToken.create(storedOwnerId, refreshToken, LocalDateTime.of(2026, 9, 6, 0, 0))
        ));

        authService.logout(refreshToken);

        InOrder order = inOrder(pushDeviceTokenService, refreshTokenRepository);
        order.verify(refreshTokenRepository).findByToken(refreshToken);
        order.verify(pushDeviceTokenService).delete(storedOwnerId);
        order.verify(refreshTokenRepository).deleteByToken(refreshToken);
    }

    @Test
    void logoutWithNoStoredRefreshTokenRemainsIdempotent() {
        String missingRefreshToken = "missing-refresh-token";
        when(refreshTokenRepository.findByToken(missingRefreshToken)).thenReturn(Optional.empty());

        authService.logout(missingRefreshToken);

        verify(refreshTokenRepository).findByToken(missingRefreshToken);
        verify(pushDeviceTokenService, never()).delete(org.mockito.ArgumentMatchers.anyLong());
        verify(refreshTokenRepository, never()).deleteByToken(missingRefreshToken);
    }
}
