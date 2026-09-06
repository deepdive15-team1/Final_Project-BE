package com.highpass.runspot.notification.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.service.PushOutboxEnqueuer;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final PushOutboxEnqueuer pushOutboxEnqueuer;

    public void notifyParticipationRequested(SessionParticipant participation) {
        Session session = participation.getSession();
        User applicant = participation.getUser();
        create(participation, session.getHostUser(), applicant, new NotificationContent(
                NotificationType.PARTICIPATION_REQUESTED,
                "새로운 러너가 대기 중이에요!",
                applicant.getName() + "님이 [" + session.getTitle() + "]에 참여를 신청했습니다.",
                NotificationActionType.APPROVE_OR_REJECT,
                NotificationActionStatus.PENDING
        ));
    }

    public void notifyParticipationApproved(SessionParticipant participation) {
        Session session = participation.getSession();
        User host = session.getHostUser();
        create(participation, participation.getUser(), host, new NotificationContent(
                NotificationType.PARTICIPATION_APPROVED,
                "참여가 확정되었습니다!",
                "[" + session.getTitle() + "] 참여 신청이 승인되었습니다.",
                NotificationActionType.NAVIGATE,
                NotificationActionStatus.NONE
        ));
        resolvePendingRequest(participation, host.getId());
    }

    public void notifyParticipationRejected(SessionParticipant participation) {
        Session session = participation.getSession();
        User host = session.getHostUser();
        create(participation, participation.getUser(), host, new NotificationContent(
                NotificationType.PARTICIPATION_REJECTED,
                "참여 신청이 거절되었습니다.",
                "[" + session.getTitle() + "] 참여 신청이 거절되었습니다.",
                NotificationActionType.NAVIGATE,
                NotificationActionStatus.NONE
        ));
        resolvePendingRequest(participation, host.getId());
    }

    public void notifyParticipantKicked(SessionParticipant participation) {
        Session session = participation.getSession();
        create(participation, participation.getUser(), session.getHostUser(), new NotificationContent(
                NotificationType.PARTICIPANT_KICKED,
                "세션 참여가 취소되었습니다.",
                "[" + session.getTitle() + "] 참여자 명단에서 제외되었습니다.",
                NotificationActionType.NAVIGATE,
                NotificationActionStatus.NONE
        ));
    }

    private void create(
            SessionParticipant participation,
            User recipient,
            User actor,
            NotificationContent content
    ) {
        Session session = participation.getSession();
        Notification notification = notificationRepository.save(Notification.builder()
                .recipientUserId(recipient.getId())
                .actorUserId(actor.getId())
                .actorName(actor.getName())
                .type(content.type())
                .title(content.title())
                .body(content.body())
                .sessionId(session.getId())
                .participationId(participation.getId())
                .actionType(content.actionType())
                .actionStatus(content.actionStatus())
                .deduplicationKey(content.type() + ":" + participation.getId() + ":" + recipient.getId())
                .build());
        pushOutboxEnqueuer.enqueue(notification);
    }

    private void resolvePendingRequest(SessionParticipant participation, Long hostUserId) {
        notificationRepository.findByTypeAndParticipationIdAndRecipientUserIdAndActionStatus(
                NotificationType.PARTICIPATION_REQUESTED,
                participation.getId(),
                hostUserId,
                NotificationActionStatus.PENDING
        ).ifPresent(Notification::resolveAction);
    }

    private record NotificationContent(
            NotificationType type,
            String title,
            String body,
            NotificationActionType actionType,
            NotificationActionStatus actionStatus
    ) {
    }
}
