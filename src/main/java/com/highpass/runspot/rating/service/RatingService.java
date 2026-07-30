package com.highpass.runspot.rating.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.rating.domain.Rating;
import com.highpass.runspot.rating.domain.RatingTargetType;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.rating.exception.RatingErrorCode;
import com.highpass.runspot.rating.exception.RatingException;
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
                .orElseThrow(() -> new RatingException(RatingErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.FINISHED) {
            throw new RatingException(RatingErrorCode.SESSION_NOT_FINISHED);
        }

        validateRaterEligibility(userId, sessionId);

        Long hostUserId = session.getHostUser().getId();

        if (ratingRepository.existsBySessionIdAndRaterIdAndTargetId(sessionId, userId, hostUserId)) {
            throw new RatingException(RatingErrorCode.ALREADY_RATED_HOST);
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
                .orElseThrow(() -> new RatingException(RatingErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.FINISHED) {
            throw new RatingException(RatingErrorCode.SESSION_NOT_FINISHED);
        }

        Long hostUserId = session.getHostUser().getId();

        // 멤버 평가는 호스트 또는 해당 세션에 출석한 참여자가 할 수 있다
        if (!userId.equals(hostUserId)) {
            validateRaterEligibility(userId, sessionId);
        }

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
                        throw new RatingException(RatingErrorCode.SELF_RATING_NOT_ALLOWED);
                    }
                    if (targetId.equals(hostUserId)) {
                        throw new RatingException(RatingErrorCode.HOST_NOT_MEMBER_TARGET);
                    }
                    if (alreadyRatedIds.contains(targetId)) {
                        throw new RatingException(RatingErrorCode.ALREADY_RATED_MEMBER);
                    }
                    if (!eligibleMemberIds.contains(targetId)) {
                        throw new RatingException(RatingErrorCode.TARGET_NOT_ELIGIBLE);
                    }

                    User target = targetUsersById.get(targetId);
                    if (target == null) {
                        throw new RatingException(RatingErrorCode.TARGET_USER_NOT_FOUND);
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
            throw new RatingException(RatingErrorCode.RATER_NOT_ELIGIBLE);
        }
    }
}
