package com.highpass.runspot.auth.service;

import com.highpass.runspot.auth.service.dto.response.AppliedRunningResponse;
import com.highpass.runspot.auth.service.dto.response.AppliedRunningsResponse;
import com.highpass.runspot.auth.service.dto.response.RecentRunningResponse;
import com.highpass.runspot.auth.service.dto.response.RecentRunningsResponse;
import com.highpass.runspot.auth.service.dto.response.UserProfileResponse;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.UserRunningStats;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStatsService {

    private final UserRepository userRepository;
    private final UserRunningStatsRepository userRunningStatsRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        UserRunningStats stats = userRunningStatsRepository.findByUser(user).orElse(null);

        return UserProfileResponse.from(user, stats);
    }

    public AppliedRunningsResponse getAppliedRunnings(Long userId, Integer limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        int pageSize = limit != null && limit > 0 ? limit : 3;

        List<SessionParticipant> participants = sessionParticipantRepository
                .findAppliedRunnings(userId, PageRequest.of(0, pageSize));

        List<AppliedRunningResponse> runnings = participants.stream()
                .map(AppliedRunningResponse::from)
                .toList();

        return AppliedRunningsResponse.of(runnings);
    }

    public RecentRunningsResponse getRecentRunnings(Long userId, Integer limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        int pageSize = limit != null && limit > 0 ? limit : 3;

        List<SessionParticipant> participants = sessionParticipantRepository
                .findRecentAttendedRunnings(userId, PageRequest.of(0, pageSize));

        List<RecentRunningResponse> runnings = participants.stream()
                .map(RecentRunningResponse::from)
                .toList();

        return RecentRunningsResponse.of(runnings);
    }

    @Transactional
    public void updateRunningStatsOnAttendance(Long userId, BigDecimal distanceKm) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        UserRunningStats stats = userRunningStatsRepository.findByUser(user)
                .orElseGet(() -> {
                    // 통계가 없으면 새로 생성
                    UserRunningStats newStats = UserRunningStats.builder()
                            .user(user)
                            .build();
                    return userRunningStatsRepository.save(newStats);
                });

        stats.incrementRunning(distanceKm);
    }
}