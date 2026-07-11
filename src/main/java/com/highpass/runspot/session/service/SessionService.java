package com.highpass.runspot.session.service;

import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.UserRunningStats;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import com.highpass.runspot.auth.service.UserStatsService;
import com.highpass.runspot.auth.service.dto.response.MyCreatedRunningsResponse;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.GenderPolicy;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.SessionStatus;
import com.highpass.runspot.session.service.dto.request.AttendanceUpdateRequest;
import com.highpass.runspot.session.service.dto.request.SessionCreateRequest;
import com.highpass.runspot.session.service.dto.request.SessionJoinRequest;
import com.highpass.runspot.session.service.dto.response.SessionParticipantResponse;
import com.highpass.runspot.session.service.dto.response.SessionResponse;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.session.exception.SessionErrorCode;
import com.highpass.runspot.session.exception.SessionException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final UserRunningStatsRepository userRunningStatsRepository;
    private final UserRepository userRepository;
    private final UserStatsService userStatsService;

    @Transactional
    public SessionResponse createSession(Long userId, SessionCreateRequest request) {
        User hostUser = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

//        // 영구 정지 확인
//        if (hostUser.isPermanentlyBanned()) {
//            throw new IllegalStateException("계정이 영구 정지되었습니다. 고객센터에 문의해주세요.");
//        }
//
//        // 이용 제한 확인
//        if (hostUser.isSuspended()) {
//            String level = hostUser.getSuspensionCount() == 1 ? "1차" : "2차(최종)";
//            throw new IllegalStateException(
//                    String.format("%s 이용 제한 중입니다. 해제일: %s",
//                            level,
//                            hostUser.getSuspensionEndDate().format(
//                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
//                            ))
//            );
//        }
//
//        // 매너온도 확인 (36.5 이상)
//        if (!hostUser.canCreateSession()) {
//            throw new IllegalStateException(
//                    String.format("세션 개설 조건을 충족하지 않습니다. 현재 매너온도: %.1f°C (필요: 36.5°C 이상)",
//                            hostUser.getMannerTemp())
//            );
//        }
//
//        // 러닝 통계 확인 (누적 거리 15km 이상, 누적 횟수 3회 이상)
//        UserRunningStats stats = userRunningStatsRepository.findByUserId(userId)
//                .orElse(null);
//
//        if (stats == null || stats.getTotalDistanceKm().compareTo(new BigDecimal("15.0")) < 0) {
//            BigDecimal currentDistance = stats != null ? stats.getTotalDistanceKm() : BigDecimal.ZERO;
//            throw new IllegalStateException(
//                    String.format("세션 개설 조건을 충족하지 않습니다. 현재 누적 거리: %.1fkm (필요: 15km 이상)",
//                            currentDistance)
//            );
//        }
//
//        if (stats.getTotalRunningCount() < 3) {
//            throw new IllegalStateException(
//                    String.format("세션 개설 조건을 충족하지 않습니다. 현재 누적 횟수: %d회 (필요: 3회 이상)",
//                            stats.getTotalRunningCount())
//            );
//        }

        Session session = request.toEntity(hostUser);
        Session savedSession = sessionRepository.save(session);

        return SessionResponse.from(savedSession);
    }

    @Transactional
    public void closeSession(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        session.close(userId);
    }

    @Transactional
    public void finishSession(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        session.finish(userId);
    }

    @Transactional
    public void joinSession(Long userId, Long sessionId, SessionJoinRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

//        // 영구 정지 확인
//        if (user.isPermanentlyBanned()) {
//            throw new IllegalStateException("계정이 영구 정지되었습니다. 고객센터에 문의해주세요.");
//        }
//
//        // 이용 제한 확인
//        if (user.isSuspended()) {
//            String level = user.getSuspensionCount() == 1 ? "1차" : "2차(최종)";
//            throw new IllegalStateException(
//                    String.format("%s 이용 제한 중입니다. 해제일: %s",
//                            level,
//                            user.getSuspensionEndDate().format(
//                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
//                            ))
//            );
//        }

        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        // 호스트 본인 신청 불가
        if (session.getHostUser().getId().equals(userId)) {
            throw new IllegalArgumentException("호스트는 참여 신청을 할 수 없습니다.");
        }

        // 중복 신청 불가
        if (sessionParticipantRepository.existsBySessionAndUser(session, user)) {
            throw new IllegalArgumentException("이미 신청한 세션입니다.");
        }

        // 세션 상태 체크 (OPEN만 가능)
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("모집 중인 세션이 아닙니다.");
        }

        // 성별 정책 체크
        validateGenderPolicy(session.getGenderPolicy(), user.getGender());

        // 인원 제한 체크 (승인된 인원 기준)
        long approvedCount = sessionParticipantRepository.countBySessionIdAndStatus(sessionId, ParticipationStatus.APPROVED);
        if (approvedCount >= session.getCapacity()) {
            throw new IllegalStateException("모집 인원이 마감되었습니다.");
        }

        // 신청 저장
        SessionParticipant participant = SessionParticipant.builder()
            .session(session)
            .user(user)
            .messageToHost(request.messageToHost())
            .build(); // status 기본값 REQUESTED, attendanceStatus 기본값 ABSENT

        sessionParticipantRepository.save(participant);
    }

    public List<SessionParticipantResponse> getJoinRequests(Long userId, Long sessionId, ParticipationStatus status) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        // 호스트 검증
        if (!Objects.equals(session.getHostUser().getId(), userId)) {
            throw new IllegalStateException("호스트만 신청 목록을 조회할 수 있습니다.");
        }

        List<SessionParticipant> participants;
        if (status == null) {
            participants = sessionParticipantRepository.findBySessionId(sessionId);
        } else {
            participants = sessionParticipantRepository.findBySessionIdAndStatus(sessionId, status);
        }

        return participants.stream()
            .map(SessionParticipantResponse::from)
            .toList();
    }

    @Transactional
    public void approveJoinRequest(Long userId, Long sessionId, Long participationId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        // 호스트 검증
        if (!Objects.equals(session.getHostUser().getId(), userId)) {
            throw new IllegalStateException("호스트만 승인할 수 있습니다.");
        }

        SessionParticipant participant = sessionParticipantRepository.findById(participationId)
            .orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));

        // 인원 마감 체크 (승인 시점에 다시 한번 체크)
        long approvedCount = sessionParticipantRepository.countBySessionIdAndStatus(sessionId, ParticipationStatus.APPROVED);
        if (approvedCount >= session.getCapacity()) {
            throw new IllegalStateException("모집 인원이 마감되어 승인할 수 없습니다.");
        }

        participant.approve();
    }

    @Transactional
    public void rejectJoinRequest(Long userId, Long sessionId, Long participationId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        // 호스트 검증
        if (!Objects.equals(session.getHostUser().getId(), userId)) {
            throw new IllegalStateException("호스트만 거절할 수 있습니다.");
        }

        SessionParticipant participant = sessionParticipantRepository.findById(participationId)
            .orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));

        participant.reject();
    }

    public List<SessionParticipantResponse> getAttendanceList(Long userId, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        // 일단 호스트만 보는 것으로 구현
        if (!Objects.equals(session.getHostUser().getId(), userId)) {
            throw new IllegalStateException("호스트만 출석부를 조회할 수 있습니다.");
        }

        // 승인된 참여자만 조회
        List<SessionParticipant> participants = sessionParticipantRepository.findBySessionIdAndStatus(sessionId, ParticipationStatus.APPROVED);

        return participants.stream()
            .map(SessionParticipantResponse::from)
            .toList();
    }

    @Transactional
    public void updateAttendance(Long userId, Long sessionId, Long participationId, AttendanceUpdateRequest request) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("세션을 찾을 수 없습니다. ID: " + sessionId));

        // 호스트 검증
        if (!Objects.equals(session.getHostUser().getId(), userId)) {
            throw new IllegalStateException("호스트만 출석 체크를 할 수 있습니다.");
        }

        SessionParticipant participant = sessionParticipantRepository.findById(participationId)
            .orElseThrow(() -> new IllegalArgumentException("참여 정보를 찾을 수 없습니다."));

        // 해당 세션의 참여자인지 확인
        if (!participant.getSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException("해당 세션의 참여자가 아닙니다.");
        }

        // 기존 출석 상태 확인
        AttendanceStatus previousStatus = participant.getAttendanceStatus();

        participant.updateAttendance(request.attendanceStatus());

        if (previousStatus != AttendanceStatus.ATTENDED
                && request.attendanceStatus() == AttendanceStatus.ATTENDED) {
            userStatsService.updateRunningStatsOnAttendance(
                    participant.getUser().getId(),
                    session.getTargetDistanceKm()
            );
        }
//
//        AttendanceStatus newStatus = request.attendanceStatus();
//
//        // 출석 상태 업데이트
//        participant.updateAttendance(newStatus);
//
//        User participantUser = participant.getUser();
//
//        // 출석 처리: 미출석 -> 출석
//        if (previousStatus != AttendanceStatus.ATTENDED
//                && newStatus == AttendanceStatus.ATTENDED) {
//
//            // 러닝 통계 업데이트
//            userStatsService.updateRunningStatsOnAttendance(
//                    participantUser.getId(),
//                    session.getTargetDistanceKm()
//            );
//
//            // 매너온도 증가 및 노쇼 카운트 초기화
//            participantUser.recordAttendance();
//        }
//
//        // 노쇼 처리: 출석 -> 미출석 또는 처음부터 미출석 확정
//        if (previousStatus != AttendanceStatus.ABSENT
//                && newStatus == AttendanceStatus.ABSENT) {
//
//            // 매너온도 감소 및 노쇼 카운트 증가 (2회 이상 시 자동 3일 정지)
//            participantUser.recordNoShow();
//        }
    }

    public List<MyCreatedRunningsResponse> getMyHostedSessions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // OPEN 상태 우선, 그 다음 최신순으로 정렬하여 3개만 조회
        List<Session> sessions = sessionRepository.findTop3ByHostUserOrderByStatusAscCreatedAtDesc(user);

        return sessions.stream()
                .map(session -> {
                    int currentParticipants = (int) sessionParticipantRepository
                            .countBySessionIdAndStatus(session.getId(), ParticipationStatus.APPROVED);
                    return MyCreatedRunningsResponse.from(session, currentParticipants);
                })
                .toList();
    }

    @Transactional
    public void kickParticipant(Long userId, Long sessionId, Long participationId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

        if (!Objects.equals(session.getHostUser().getId(), userId)) {
            throw new SessionException(SessionErrorCode.KICK_NOT_HOST);
        }

        if (session.getStatus() != SessionStatus.OPEN && session.getStatus() != SessionStatus.CLOSED) {
            throw new SessionException(SessionErrorCode.KICK_INVALID_STATUS);
        }

        SessionParticipant participant = sessionParticipantRepository.findById(participationId)
            .orElseThrow(() -> new SessionException(SessionErrorCode.PARTICIPANT_NOT_FOUND));

        if (!participant.getSession().getId().equals(sessionId)) {
            throw new SessionException(SessionErrorCode.PARTICIPANT_SESSION_MISMATCH);
        }

        participant.kick();
    }

    private void validateGenderPolicy(GenderPolicy policy, Gender userGender) {
        if (policy == GenderPolicy.MALE_ONLY && userGender != Gender.MALE) {
            throw new IllegalArgumentException("남성만 참여 가능한 세션입니다.");
        }
        if (policy == GenderPolicy.FEMALE_ONLY && userGender != Gender.FEMALE) {
            throw new IllegalArgumentException("여성만 참여 가능한 세션입니다.");
        }
    }
}
