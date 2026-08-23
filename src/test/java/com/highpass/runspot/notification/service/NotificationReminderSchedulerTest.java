package com.highpass.runspot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationReminderSchedulerTest extends NotificationReminderIntegrationSupport {

    @Test
    void 고정시각의_30분_이상_31분_미만_OPEN_CLOSED_세션만_대상이다() {
        User host = user("호스트");
        session(host, FIXED_NOW.plusMinutes(30).minusSeconds(1), SessionStatus.OPEN);
        Session atWindowStart = session(host, FIXED_NOW.plusMinutes(30), SessionStatus.OPEN);
        Session beforeWindowEnd = session(host, FIXED_NOW.plusMinutes(31).minusSeconds(1), SessionStatus.CLOSED);
        session(host, FIXED_NOW.plusMinutes(31), SessionStatus.OPEN);
        session(host, FIXED_NOW.plusMinutes(30).plusSeconds(30), SessionStatus.CANCELED);
        session(host, FIXED_NOW.plusMinutes(30).plusSeconds(30), SessionStatus.IN_PROGRESS);
        session(host, FIXED_NOW.plusMinutes(30).plusSeconds(30), SessionStatus.FINISHED);

        scheduler.sendSessionStartReminders();

        assertThat(notificationRepository.findAll())
                .extracting(Notification::getSessionId)
                .containsExactlyInAnyOrder(atWindowStart.getId(), beforeWindowEnd.getId());
    }

    @Test
    void 호스트와_APPROVED_참여자만_사용자_ID로_중복제거해_정확한_리마인더를_받는다() {
        User host = user("호스트");
        User approved = user("승인");
        User requested = user("대기");
        User rejected = user("거절");
        User canceled = user("취소");
        User kicked = user("강퇴");
        Session session = session(host, FIXED_NOW.plusMinutes(30), SessionStatus.OPEN);
        participant(session, host, ParticipationStatus.APPROVED);
        participant(session, approved, ParticipationStatus.APPROVED);
        participant(session, requested, ParticipationStatus.REQUESTED);
        participant(session, rejected, ParticipationStatus.REJECTED);
        participant(session, canceled, ParticipationStatus.CANCELED);
        participant(session, kicked, ParticipationStatus.KICKED);

        scheduler.sendSessionStartReminders();

        List<Notification> reminders = notificationRepository.findAll();
        assertThat(reminders).extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(host.getId(), approved.getId());
        assertThat(reminders).allSatisfy(reminder -> {
            assertThat(reminder.getType()).isEqualTo(NotificationType.SESSION_START_REMINDER);
            assertThat(reminder.getTitle()).isEqualTo("러닝 시작 30분 전");
            assertThat(reminder.getBody()).isEqualTo("스트레칭을 잊지 마세요! [한강 야간 러닝] 러닝이 곧 시작됩니다.");
            assertThat(reminder.getActorUserId()).isNull();
            assertThat(reminder.getActorName()).isNull();
            assertThat(reminder.getActorProfileImageUrl()).isNull();
            assertThat(reminder.getParticipationId()).isNull();
            assertThat(reminder.getActionType()).isEqualTo(NotificationActionType.NAVIGATE);
            assertThat(reminder.getActionStatus()).isEqualTo(NotificationActionStatus.NONE);
            assertThat(reminder.getSessionId()).isEqualTo(session.getId());
            assertThat(reminder.getDeduplicationKey()).isEqualTo(
                    "SESSION_START_REMINDER:" + session.getId() + ":" + reminder.getRecipientUserId());
        });
    }
}
