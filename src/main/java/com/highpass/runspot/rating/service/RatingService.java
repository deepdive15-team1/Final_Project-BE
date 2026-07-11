package com.highpass.runspot.rating.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.rating.domain.Rating;
import com.highpass.runspot.rating.domain.RatingTargetType;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.rating.service.dto.request.HostRatingRequest;
import com.highpass.runspot.rating.service.dto.request.MemberRatingRequest;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.SessionStatus;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {

    private final RatingRepository ratingRepository;
    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final UserRepository userRepository;

    @Transactional
    public void rateHost(Long userId, Long sessionId, HostRatingRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        if (session.getStatus() != SessionStatus.FINISHED) {
            throw new IllegalStateException("종료된 세션만 평가할 수 있습니다.");
        }

        validateRaterEligibility(userId, sessionId);

        Long hostUserId = session.getHostUser().getId();

        if (ratingRepository.existsBySessionIdAndRaterIdAndTargetId(sessionId, userId, hostUserId)) {
            throw new IllegalStateException("이미 호스트 평가를 완료했습니다.");
        }

        Rating rating = Rating.builder()
                .session(session)
                .rater(userRepository.getReferenceById(userId))
                .target(session.getHostUser())
                .ratingType(request.rating())
                .targetType(RatingTargetType.HOST)
                .tags(request.tags())
                .build();

        ratingRepository.save(rating);
    }

    @Transactional
    public void rateMembers(Long userId, Long sessionId, MemberRatingRequest request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        if (session.getStatus() != SessionStatus.FINISHED) {
            throw new IllegalStateException("종료된 세션만 평가할 수 있습니다.");
        }

        validateRaterEligibility(userId, sessionId);

        Long hostUserId = session.getHostUser().getId();
        Set<Long> alreadyRatedIds = Set.copyOf(
                ratingRepository.findTargetIdsBySessionIdAndRaterId(sessionId, userId));

        List<Long> targetUserIds = request.ratings().stream()
                .map(MemberRatingRequest.MemberRatingItem::targetUserId)
                .toList();

        Map<Long, User> targetUsersById = userRepository.findAllById(targetUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // 해당 세션의 APPROVED+ATTENDED 참여자 ID 목록
        Set<Long> eligibleMemberIds = sessionParticipantRepository
                .findBySessionIdAndStatusWithUser(sessionId, ParticipationStatus.APPROVED)
                .stream()
                .filter(sp -> sp.getAttendanceStatus() == AttendanceStatus.ATTENDED)
                .map(sp -> sp.getUser().getId())
                .collect(Collectors.toSet());

        List<Rating> ratingsToSave = request.ratings().stream()
                .map(item -> {
                    Long targetId = item.targetUserId();

                    if (targetId.equals(userId)) {
                        throw new IllegalArgumentException("자기 자신은 평가할 수 없습니다.");
                    }
                    if (targetId.equals(hostUserId)) {
                        throw new IllegalArgumentException("호스트는 멤버 평가 대상이 아닙니다. 호스트 평가 API를 이용해주세요.");
                    }
                    if (alreadyRatedIds.contains(targetId)) {
                        throw new IllegalStateException("이미 평가한 멤버가 포함되어 있습니다. targetUserId: " + targetId);
                    }
                    if (!eligibleMemberIds.contains(targetId)) {
                        throw new IllegalArgumentException("평가 대상이 해당 세션의 출석 참여자가 아닙니다. targetUserId: " + targetId);
                    }

                    User target = targetUsersById.get(targetId);
                    if (target == null) {
                        throw new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + targetId);
                    }

                    return Rating.builder()
                            .session(session)
                            .rater(userRepository.getReferenceById(userId))
                            .target(target)
                            .ratingType(item.rating())
                            .targetType(RatingTargetType.MEMBER)
                            .build();
                })
                .toList();

        ratingRepository.saveAll(ratingsToSave);
    }

    private void validateRaterEligibility(Long userId, Long sessionId) {
        boolean isEligible = sessionParticipantRepository
                .findBySessionIdAndStatusWithUser(sessionId, ParticipationStatus.APPROVED)
                .stream()
                .anyMatch(sp -> sp.getUser().getId().equals(userId)
                        && sp.getAttendanceStatus() == AttendanceStatus.ATTENDED);

        if (!isEligible) {
            throw new IllegalStateException("해당 세션에 출석한 참여자만 평가할 수 있습니다.");
        }
    }
}
