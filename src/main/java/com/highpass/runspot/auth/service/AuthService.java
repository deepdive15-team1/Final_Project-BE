package com.highpass.runspot.auth.service;

import com.highpass.runspot.auth.domain.RefreshToken;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.RefreshTokenRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import com.highpass.runspot.auth.service.dto.request.LoginRequest;
import com.highpass.runspot.auth.service.dto.request.SignupRequest;
import com.highpass.runspot.auth.service.dto.response.TokenResponse;
import com.highpass.runspot.common.jwt.JwtProvider;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.service.PushDeviceTokenService;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRunningStatsRepository userRunningStatsRepository;
    private final NotificationRepository notificationRepository;
    private final PushOutboxRepository pushOutboxRepository;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final RatingRepository ratingRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final SessionRepository sessionRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다");
        }

        User.UserBuilder userBuilder = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .ageGroup(request.getAgeGroup())
                .gender(request.getGender())
                .mannerTemp(new BigDecimal("36.5"));

        if (request.getWeeklyRuns() != null) {
            userBuilder.weeklyRunningGoal(request.getWeeklyRuns());
        } else {
            userBuilder.weeklyRunningGoal(3);
        }

        if (request.getAvgPaceMinPerKm() != null) {
            userBuilder.pacePreferenceSec(request.getAvgPaceInSeconds());
        } else {
            userBuilder.pacePreferenceSec(360);
        }

        return userRepository.save(userBuilder.build());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다");
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());
        saveRefreshToken(user.getId(), refreshToken);

        return TokenResponse.of(accessToken, refreshToken, user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다");
        }

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리프레시 토큰입니다"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("만료된 리프레시 토큰입니다");
        }

        Long userId = jwtProvider.getUserIdFromToken(refreshToken);
        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        LocalDateTime newExpiry = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshTokenExpirationMs() / 1000);
        stored.update(newRefreshToken, newExpiry);

        return TokenResponse.ofTokensOnly(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(storedRefreshToken -> {
            pushDeviceTokenService.delete(storedRefreshToken.getUserId());
            refreshTokenRepository.deleteByToken(refreshToken);
        });
    }

    @Transactional
    public void withdraw(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("존재하지 않는 사용자입니다");
        }

        // users를 참조하는 자식 레코드를 외래키 의존 순서대로 먼저 제거한다.
        // 주최 세션에 달린 다른 사용자의 평가/참가 기록도 세션보다 먼저 삭제해야 한다.
        pushOutboxRepository.deleteAllRelatedToUserNotifications(userId);
        pushDeviceTokenService.delete(userId);
        notificationRepository.deleteAllRelatedToUser(userId);
        ratingRepository.deleteAllRelatedToUser(userId);
        sessionParticipantRepository.deleteAllRelatedToUser(userId);
        sessionRepository.deleteByHostUserId(userId);
        userRunningStatsRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);
    }

    private void saveRefreshToken(Long userId, String token) {
        LocalDateTime expiry = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshTokenExpirationMs() / 1000);

        refreshTokenRepository.findByUserId(userId).ifPresentOrElse(
                rt -> rt.update(token, expiry),
                () -> refreshTokenRepository.save(RefreshToken.create(userId, token, expiry))
        );
    }
}
