package com.highpass.runspot.auth.service;

import com.highpass.runspot.auth.domain.RefreshToken;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.RefreshTokenRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.service.dto.request.LoginRequest;
import com.highpass.runspot.auth.service.dto.request.SignupRequest;
import com.highpass.runspot.auth.service.dto.response.TokenResponse;
import com.highpass.runspot.common.jwt.JwtProvider;
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
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    @Transactional
    public void withdraw(Long userId) {
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
