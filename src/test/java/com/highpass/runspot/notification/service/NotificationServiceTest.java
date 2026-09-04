package com.highpass.runspot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.service.PushOutboxEnqueuer;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
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
class NotificationServiceTest {

    private static final Long HOST_ID = 1L;
    private static final Long APPLICANT_ID = 2L;
    private static final Long PARTICIPATION_ID = 3L;
    private static final Long SESSION_ID = 4L;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PushOutboxEnqueuer pushOutboxEnqueuer;
    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;
    @InjectMocks
    private NotificationService notificationService;

    private SessionParticipant participation;

    @BeforeEach
    void setUp() {
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        User host = User.builder().id(HOST_ID).name("호스트").build();
        User applicant = User.builder().id(APPLICANT_ID).name("신청자").build();
        Session session = Session.builder().id(SESSION_ID).hostUser(host).title("한강 야간 러닝").build();
        participation = SessionParticipant.builder()
                .id(PARTICIPATION_ID)
                .session(session)
                .user(applicant)
                .build();
    }

    @Test
    void 저장된_알림을_아웃박스_인큐어에게_전달한다() {
        notificationService.notifyParticipationRequested(participation);

        Notification notification = capturedNotification();

        verify(pushOutboxEnqueuer).enqueue(notification);
    }

    @Test
    void 참여_신청_알림은_호스트에게_대기_액션으로_저장된다() {
        notificationService.notifyParticipationRequested(participation);

        Notification notification = capturedNotification();

        assertNotification(notification, NotificationType.PARTICIPATION_REQUESTED, HOST_ID, APPLICANT_ID,
                "신청자", "새로운 러너가 대기 중이에요!", "신청자님이 [한강 야간 러닝]에 참여를 신청했습니다.",
                NotificationActionType.APPROVE_OR_REJECT, NotificationActionStatus.PENDING);
    }

    @Test
    void 참여_승인_알림은_신청자에게_저장하고_기존_요청을_해결한다() {
        Notification request = pendingRequest();
        when(notificationRepository.findByTypeAndParticipationIdAndRecipientUserIdAndActionStatus(
                NotificationType.PARTICIPATION_REQUESTED,
                PARTICIPATION_ID,
                HOST_ID,
                NotificationActionStatus.PENDING
        )).thenReturn(Optional.of(request));

        notificationService.notifyParticipationApproved(participation);

        Notification notification = capturedNotification();
        assertNotification(notification, NotificationType.PARTICIPATION_APPROVED, APPLICANT_ID, HOST_ID,
                "호스트", "참여가 확정되었습니다!", "[한강 야간 러닝] 참여 신청이 승인되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE);
        assertThat(request.getActionStatus()).isEqualTo(NotificationActionStatus.RESOLVED);
    }

    @Test
    void 참여_승인_알림은_기존_요청이_없어도_신청자에게_저장된다() {
        when(notificationRepository.findByTypeAndParticipationIdAndRecipientUserIdAndActionStatus(
                NotificationType.PARTICIPATION_REQUESTED,
                PARTICIPATION_ID,
                HOST_ID,
                NotificationActionStatus.PENDING
        )).thenReturn(Optional.empty());

        notificationService.notifyParticipationApproved(participation);

        Notification notification = capturedNotification();
        assertNotification(notification, NotificationType.PARTICIPATION_APPROVED, APPLICANT_ID, HOST_ID,
                "호스트", "참여가 확정되었습니다!", "[한강 야간 러닝] 참여 신청이 승인되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE);
    }

    @Test
    void 참여_거절_알림은_기존_요청이_없어도_신청자에게_저장된다() {
        when(notificationRepository.findByTypeAndParticipationIdAndRecipientUserIdAndActionStatus(
                NotificationType.PARTICIPATION_REQUESTED,
                PARTICIPATION_ID,
                HOST_ID,
                NotificationActionStatus.PENDING
        )).thenReturn(Optional.empty());

        notificationService.notifyParticipationRejected(participation);

        Notification notification = capturedNotification();
        assertNotification(notification, NotificationType.PARTICIPATION_REJECTED, APPLICANT_ID, HOST_ID,
                "호스트", "참여 신청이 거절되었습니다.", "[한강 야간 러닝] 참여 신청이 거절되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE);
    }

    @Test
    void 참여자_강퇴_알림은_강퇴된_사용자에게_저장된다() {
        notificationService.notifyParticipantKicked(participation);

        Notification notification = capturedNotification();

        assertNotification(notification, NotificationType.PARTICIPANT_KICKED, APPLICANT_ID, HOST_ID,
                "호스트", "세션 참여가 취소되었습니다.", "[한강 야간 러닝] 참여자 명단에서 제외되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE);
    }

    private Notification capturedNotification() {
        verify(notificationRepository).save(notificationCaptor.capture());
        return notificationCaptor.getValue();
    }

    private Notification pendingRequest() {
        return Notification.builder()
                .recipientUserId(HOST_ID)
                .type(NotificationType.PARTICIPATION_REQUESTED)
                .title("기존 요청")
                .body("기존 요청")
                .sessionId(SESSION_ID)
                .participationId(PARTICIPATION_ID)
                .actionType(NotificationActionType.APPROVE_OR_REJECT)
                .actionStatus(NotificationActionStatus.PENDING)
                .deduplicationKey("PARTICIPATION_REQUESTED:3:1")
                .build();
    }

    private void assertNotification(
            Notification notification,
            NotificationType type,
            Long recipientUserId,
            Long actorUserId,
            String actorName,
            String title,
            String body,
            NotificationActionType actionType,
            NotificationActionStatus actionStatus
    ) {
        assertThat(notification.getRecipientUserId()).isEqualTo(recipientUserId);
        assertThat(notification.getActorUserId()).isEqualTo(actorUserId);
        assertThat(notification.getActorName()).isEqualTo(actorName);
        assertThat(notification.getActorProfileImageUrl()).isNull();
        assertThat(notification.getType()).isEqualTo(type);
        assertThat(notification.getTitle()).isEqualTo(title);
        assertThat(notification.getBody()).isEqualTo(body);
        assertThat(notification.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(notification.getParticipationId()).isEqualTo(PARTICIPATION_ID);
        assertThat(notification.getActionType()).isEqualTo(actionType);
        assertThat(notification.getActionStatus()).isEqualTo(actionStatus);
        assertThat(notification.getDeduplicationKey()).isEqualTo(type + ":3:" + recipientUserId);
    }
}
