package com.highpass.runspot.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.domain.dao.UserRunningStatsRepository;
import com.highpass.runspot.auth.service.UserStatsService;
import com.highpass.runspot.auth.service.dto.response.MyCreatedRunningsResponse;
import com.highpass.runspot.common.util.GeometryUtil;
import com.highpass.runspot.rating.domain.RatingTargetType;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.SessionStatus;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.session.exception.SessionErrorCode;
import com.highpass.runspot.session.exception.SessionException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionParticipantRepository sessionParticipantRepository;
    @Mock
    private UserRunningStatsRepository userRunningStatsRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserStatsService userStatsService;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SessionService sessionService;

    private static final Long HOST_ID = 1L;
    private User host;

    @BeforeEach
    void setUp() {
        host = User.builder().id(HOST_ID).build();
    }

    private Session session(Long id, SessionStatus status) {
        return Session.builder()
                .id(id)
                .hostUser(host)
                .status(status)
                .capacity(2)
                .location(GeometryUtil.createPoint(BigDecimal.ZERO, BigDecimal.ZERO))
                .build();
    }

    @Test
    void 호스트가_요청된_참여자를_승인한다() {
        Session session = session(10L, SessionStatus.OPEN);
        SessionParticipant participant = participant(50L, session, ParticipationStatus.REQUESTED);
        when(sessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.countBySessionIdAndStatus(10L, ParticipationStatus.APPROVED))
                .thenReturn(1L);

        sessionService.approveJoinRequest(HOST_ID, 10L, 50L);

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
        InOrder lockOrder = inOrder(sessionRepository, sessionParticipantRepository);
        lockOrder.verify(sessionRepository).findByIdForUpdate(10L);
        lockOrder.verify(sessionParticipantRepository).findByIdForUpdate(50L);
        lockOrder.verify(sessionParticipantRepository)
                .countBySessionIdAndStatus(10L, ParticipationStatus.APPROVED);
    }

    @Test
    void 호스트가_요청된_참여자를_거절한다() {
        Session session = session(10L, SessionStatus.OPEN);
        SessionParticipant participant = participant(50L, session, ParticipationStatus.REQUESTED);
        when(sessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(participant));

        sessionService.rejectJoinRequest(HOST_ID, 10L, 50L);

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.REJECTED);
        InOrder lockOrder = inOrder(sessionRepository, sessionParticipantRepository);
        lockOrder.verify(sessionRepository).findByIdForUpdate(10L);
        lockOrder.verify(sessionParticipantRepository).findByIdForUpdate(50L);
    }

    @Test
    void URL_세션과_다른_세션의_참여자는_승인할_수_없다() {
        Session requestedSession = session(10L, SessionStatus.OPEN);
        Session otherSession = session(20L, SessionStatus.OPEN);
        SessionParticipant participant = participant(50L, otherSession, ParticipationStatus.REQUESTED);
        when(sessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(requestedSession));
        when(sessionParticipantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> sessionService.approveJoinRequest(HOST_ID, 10L, 50L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.REQUESTED);
        verify(sessionParticipantRepository, never())
                .countBySessionIdAndStatus(any(), eq(ParticipationStatus.APPROVED));
    }

    @Test
    void URL_세션과_다른_세션의_참여자는_거절할_수_없다() {
        Session requestedSession = session(10L, SessionStatus.OPEN);
        Session otherSession = session(20L, SessionStatus.OPEN);
        SessionParticipant participant = participant(50L, otherSession, ParticipationStatus.REQUESTED);
        when(sessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(requestedSession));
        when(sessionParticipantRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> sessionService.rejectJoinRequest(HOST_ID, 10L, 50L))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.REQUESTED);
    }

    @Test
    void 러닝_시작_후에는_참여자를_강퇴할_수_있다() {
        Session session = session(10L, SessionStatus.IN_PROGRESS);
        SessionParticipant participant = SessionParticipant.builder()
                .id(50L)
                .session(session)
                .status(ParticipationStatus.APPROVED)
                .build();
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findById(50L)).thenReturn(Optional.of(participant));

        sessionService.kickParticipant(HOST_ID, 10L, 50L);

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.KICKED);
    }

    @Test
    void 러닝이_시작되지_않은_세션에서는_강퇴할_수_없다() {
        Session session = session(10L, SessionStatus.CLOSED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.kickParticipant(HOST_ID, 10L, 50L))
                .isInstanceOf(SessionException.class)
                .hasFieldOrPropertyWithValue("exceptionType", SessionErrorCode.KICK_INVALID_STATUS);
    }

    @Test
    void 러닝이_종료된_세션에서도_더이상_강퇴할_수_없다() {
        // FINISHED는 이제 "러닝 실제 종료(평가 가능)" 상태이므로 강퇴 대상이 아니다
        Session session = session(10L, SessionStatus.FINISHED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.kickParticipant(HOST_ID, 10L, 50L))
                .isInstanceOf(SessionException.class)
                .hasFieldOrPropertyWithValue("exceptionType", SessionErrorCode.KICK_INVALID_STATUS);
    }

    @Test
    void 호스트가_아니면_강퇴할_수_없다() {
        Session session = session(10L, SessionStatus.IN_PROGRESS);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.kickParticipant(999L, 10L, 50L))
                .isInstanceOf(SessionException.class)
                .hasFieldOrPropertyWithValue("exceptionType", SessionErrorCode.KICK_NOT_HOST);
    }

    @Test
    void startSession은_세션을_IN_PROGRESS로_전환한다() {
        Session session = session(10L, SessionStatus.CLOSED);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        sessionService.startSession(HOST_ID, 10L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void finishSession은_IN_PROGRESS_세션을_FINISHED로_전환한다() {
        Session session = session(10L, SessionStatus.IN_PROGRESS);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        sessionService.finishSession(HOST_ID, 10L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    void 평가가_끝난_FINISHED_세션은_내가_개설한_러닝_목록에서_제외된다() {
        Session open = session(10L, SessionStatus.OPEN);
        Session inProgress = session(20L, SessionStatus.IN_PROGRESS);
        Session finishedRated = session(30L, SessionStatus.FINISHED);
        Session finishedNotRated = session(40L, SessionStatus.FINISHED);

        when(userRepository.findById(HOST_ID)).thenReturn(Optional.of(host));
        when(sessionRepository.findByHostUserOrderByStatusAscCreatedAtDesc(host))
                .thenReturn(List.of(open, inProgress, finishedRated, finishedNotRated));

        List<SessionParticipant> eligibleMembers = List.of(
                attendedParticipant(finishedRated),
                attendedParticipant(finishedRated)
        );
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(30L, ParticipationStatus.APPROVED))
                .thenReturn(eligibleMembers);
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(40L, ParticipationStatus.APPROVED))
                .thenReturn(List.of(attendedParticipant(finishedNotRated), attendedParticipant(finishedNotRated)));

        when(ratingRepository.countBySessionIdAndRaterIdAndTargetType(30L, HOST_ID, RatingTargetType.MEMBER))
                .thenReturn(2L); // 평가 완료
        when(ratingRepository.countBySessionIdAndRaterIdAndTargetType(40L, HOST_ID, RatingTargetType.MEMBER))
                .thenReturn(0L); // 평가 미완료

        when(sessionParticipantRepository.countBySessionIdAndStatus(any(), eq(ParticipationStatus.APPROVED)))
                .thenReturn(0L);

        List<MyCreatedRunningsResponse> result = sessionService.getMyHostedSessions(HOST_ID);

        assertThat(result).extracting(MyCreatedRunningsResponse::id)
                .containsExactly(10L, 20L, 40L);
    }

    private SessionParticipant attendedParticipant(Session session) {
        return SessionParticipant.builder()
                .session(session)
                .user(User.builder().id(2L).build())
                .status(ParticipationStatus.APPROVED)
                .attendanceStatus(AttendanceStatus.ATTENDED)
                .build();
    }

    @Test
    void 다른_세션의_참여신청은_승인할_수_없다() {
        Session target = session(10L, SessionStatus.OPEN);
        Session other = session(20L, SessionStatus.OPEN);
        SessionParticipant participant = SessionParticipant.builder()
                .id(100L).session(other).user(User.builder().id(2L).build()).build();
        when(sessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        when(sessionParticipantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> sessionService.approveJoinRequest(HOST_ID, 10L, 100L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 승인된_인원이_정원에_도달하면_추가_승인을_거부한다() {
        Session target = Session.builder().id(10L).hostUser(host).status(SessionStatus.OPEN).capacity(1).build();
        SessionParticipant participant = SessionParticipant.builder()
                .id(100L).session(target).user(User.builder().id(2L).build()).build();
        when(sessionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(target));
        when(sessionParticipantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(participant));
        when(sessionParticipantRepository.countBySessionIdAndStatus(10L, ParticipationStatus.APPROVED))
                .thenReturn(1L);

        assertThatThrownBy(() -> sessionService.approveJoinRequest(HOST_ID, 10L, 100L))
                .isInstanceOf(SessionException.class)
                .hasFieldOrPropertyWithValue("exceptionType", SessionErrorCode.SESSION_CAPACITY_EXCEEDED);
    }

    private SessionParticipant participant(Long id, Session session, ParticipationStatus status) {
        return SessionParticipant.builder()
                .id(id)
                .session(session)
                .user(User.builder().id(2L).build())
                .status(status)
                .build();
    }
}
