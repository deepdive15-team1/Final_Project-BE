package com.highpass.runspot.rating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.rating.domain.Rating;
import com.highpass.runspot.rating.domain.RatingType;
import com.highpass.runspot.rating.domain.dao.RatingRepository;
import com.highpass.runspot.rating.exception.RatingErrorCode;
import com.highpass.runspot.rating.exception.RatingException;
import com.highpass.runspot.rating.service.dto.request.HostRatingRequest;
import com.highpass.runspot.rating.service.dto.request.MemberRatingRequest;
import com.highpass.runspot.rating.service.dto.request.MemberRatingRequest.MemberRatingItem;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.SessionStatus;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private SessionParticipantRepository sessionParticipantRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RatingService ratingService;

    @Captor
    private ArgumentCaptor<List<Rating>> ratingsCaptor;

    private static final Long SESSION_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long OTHER_MEMBER_ID = 3L;

    private User host;
    private Session session;

    @BeforeEach
    void setUp() {
        host = User.builder().id(HOST_ID).build();
        session = Session.builder()
                .id(SESSION_ID)
                .hostUser(host)
                .status(SessionStatus.FINISHED)
                .build();
    }

    private SessionParticipant attendedParticipant(Long userId) {
        return SessionParticipant.builder()
                .session(session)
                .user(User.builder().id(userId).build())
                .status(ParticipationStatus.APPROVED)
                .attendanceStatus(AttendanceStatus.ATTENDED)
                .build();
    }

    @Test
    void 호스트는_출석한_멤버가_아니어도_멤버를_평가할_수_있다() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(ratingRepository.findTargetIdsBySessionIdAndRaterId(SESSION_ID, HOST_ID)).thenReturn(List.of());
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(SESSION_ID, ParticipationStatus.APPROVED))
                .thenReturn(List.of(attendedParticipant(MEMBER_ID)));
        User targetUser = User.builder().id(MEMBER_ID).build();
        when(userRepository.findAllById(List.of(MEMBER_ID))).thenReturn(List.of(targetUser));
        when(userRepository.getReferenceById(HOST_ID)).thenReturn(host);

        MemberRatingRequest request = new MemberRatingRequest(
                List.of(new MemberRatingItem(MEMBER_ID, RatingType.POSITIVE)));

        ratingService.rateMembers(HOST_ID, SESSION_ID, request);

        verify(ratingRepository).saveAll(ratingsCaptor.capture());
        assertThat(ratingsCaptor.getValue()).hasSize(1);
    }

    @Test
    void 출석하지_않은_비호스트_사용자는_멤버를_평가할_수_없다() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(SESSION_ID, ParticipationStatus.APPROVED))
                .thenReturn(List.of()); // 출석한 참여자가 없어 해당 유저는 자격이 없음

        MemberRatingRequest request = new MemberRatingRequest(
                List.of(new MemberRatingItem(MEMBER_ID, RatingType.POSITIVE)));

        assertThatThrownBy(() -> ratingService.rateMembers(999L, SESSION_ID, request))
                .isInstanceOf(RatingException.class)
                .hasFieldOrPropertyWithValue("exceptionType", RatingErrorCode.RATER_NOT_ELIGIBLE);
    }

    @Test
    void 출석한_참여자끼리는_기존처럼_서로_평가할_수_있다() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(ratingRepository.findTargetIdsBySessionIdAndRaterId(SESSION_ID, MEMBER_ID)).thenReturn(List.of());
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(SESSION_ID, ParticipationStatus.APPROVED))
                .thenReturn(List.of(attendedParticipant(MEMBER_ID), attendedParticipant(OTHER_MEMBER_ID)));
        User targetUser = User.builder().id(OTHER_MEMBER_ID).build();
        when(userRepository.findAllById(List.of(OTHER_MEMBER_ID))).thenReturn(List.of(targetUser));
        when(userRepository.getReferenceById(MEMBER_ID)).thenReturn(User.builder().id(MEMBER_ID).build());

        MemberRatingRequest request = new MemberRatingRequest(
                List.of(new MemberRatingItem(OTHER_MEMBER_ID, RatingType.POSITIVE)));

        ratingService.rateMembers(MEMBER_ID, SESSION_ID, request);

        verify(ratingRepository).saveAll(ratingsCaptor.capture());
        assertThat(ratingsCaptor.getValue()).hasSize(1);
    }

    @Test
    void 세션이_종료되지_않았으면_호스트도_멤버를_평가할_수_없다() {
        session = Session.builder().id(SESSION_ID).hostUser(host).status(SessionStatus.IN_PROGRESS).build();
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        MemberRatingRequest request = new MemberRatingRequest(
                List.of(new MemberRatingItem(MEMBER_ID, RatingType.POSITIVE)));

        assertThatThrownBy(() -> ratingService.rateMembers(HOST_ID, SESSION_ID, request))
                .isInstanceOf(RatingException.class)
                .hasFieldOrPropertyWithValue("exceptionType", RatingErrorCode.SESSION_NOT_FINISHED);
    }

    @Test
    void 멤버는_호스트를_멤버_평가_대상으로_지정할_수_없다() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(ratingRepository.findTargetIdsBySessionIdAndRaterId(SESSION_ID, MEMBER_ID)).thenReturn(List.of());
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(SESSION_ID, ParticipationStatus.APPROVED))
                .thenReturn(List.of(attendedParticipant(MEMBER_ID)));
        when(userRepository.findAllById(List.of(HOST_ID))).thenReturn(List.of(host));

        MemberRatingRequest request = new MemberRatingRequest(
                List.of(new MemberRatingItem(HOST_ID, RatingType.POSITIVE)));

        assertThatThrownBy(() -> ratingService.rateMembers(MEMBER_ID, SESSION_ID, request))
                .isInstanceOf(RatingException.class)
                .hasFieldOrPropertyWithValue("exceptionType", RatingErrorCode.HOST_NOT_MEMBER_TARGET);
    }

    @Test
    void 참여자는_출석했다면_호스트를_평가할_수_있다() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionParticipantRepository.findBySessionIdAndStatusWithUser(SESSION_ID, ParticipationStatus.APPROVED))
                .thenReturn(List.of(attendedParticipant(MEMBER_ID)));
        when(ratingRepository.existsBySessionIdAndRaterIdAndTargetId(SESSION_ID, MEMBER_ID, HOST_ID)).thenReturn(false);
        when(userRepository.getReferenceById(MEMBER_ID)).thenReturn(User.builder().id(MEMBER_ID).build());

        HostRatingRequest request = new HostRatingRequest(RatingType.POSITIVE, List.of());

        ratingService.rateHost(MEMBER_ID, SESSION_ID, request);

        verify(ratingRepository).save(any(Rating.class));
    }
}
