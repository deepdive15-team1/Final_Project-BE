package com.highpass.runspot.auth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.dao.RefreshTokenRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import com.highpass.runspot.common.jwt.JwtProvider;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
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
                notificationRepository,
                ratingRepository,
                sessionParticipantRepository,
                sessionRepository,
                userRunningStatsRepository,
                refreshTokenRepository,
                userRepository
        );
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
        verify(ratingRepository, never()).deleteAllRelatedToUser(userId);
        verify(userRepository, never()).deleteById(userId);
    }
}
