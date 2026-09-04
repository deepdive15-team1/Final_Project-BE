package com.highpass.runspot.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.ChatMemberRole;
import com.highpass.runspot.chat.domain.ChatRoom;
import com.highpass.runspot.chat.domain.ChatRoomMember;
import com.highpass.runspot.chat.outbox.ChatOutboxRepository;
import com.highpass.runspot.chat.repository.ChatMessageRepository;
import com.highpass.runspot.chat.repository.ChatRoomMemberRepository;
import com.highpass.runspot.chat.repository.ChatRoomRepository;
import com.highpass.runspot.common.util.GeometryUtil;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.session.domain.GenderPolicy;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.RunType;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionParticipant;
import com.highpass.runspot.session.domain.SessionStatus;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.session.service.dto.request.SessionJoinRequest;
import com.highpass.runspot.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class SessionNotificationIntegrationTest extends MySqlContainerSupport {

    private static final AtomicLong USER_SEQUENCE = new AtomicLong();

    @Autowired
    private SessionService sessionService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionParticipantRepository sessionParticipantRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private ChatOutboxRepository chatOutboxRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    private final List<Long> taskSessionIds = new ArrayList<>();
    private final List<Long> taskParticipantIds = new ArrayList<>();
    private final List<Long> taskNotificationIds = new ArrayList<>();
    private final List<Long> taskUserIds = new ArrayList<>();
    private final List<Long> taskChatRoomIds = new ArrayList<>();

    @AfterEach
    void cleanTaskOwnedRows() {
        deleteTaskOwnedChatRows();
        notificationRepository.deleteAllById(taskNotificationIds);
        sessionParticipantRepository.deleteAllById(taskParticipantIds);
        sessionRepository.deleteAllById(taskSessionIds);
        userRepository.deleteAllById(taskUserIds);
    }

    @Test
    void 신청_후_승인하면_요청을_해결하고_정확한_승인_알림을_저장한다() {
        User host = user("승인 호스트");
        User applicant = user("승인 신청자");
        Session session = session(host, "승인 테스트 러닝");
        createGroupRoom(session);

        SessionParticipant participation = join(host, applicant, session);
        sessionService.approveJoinRequest(host.getId(), session.getId(), participation.getId());

        List<Notification> notifications = reloadedNotifications(session.getId());

        assertThat(notifications).hasSize(2);
        assertNotification(notification(notifications, NotificationType.PARTICIPATION_REQUESTED),
                NotificationType.PARTICIPATION_REQUESTED, host.getId(), applicant.getId(), applicant.getName(),
                "새로운 러너가 대기 중이에요!", "승인 신청자님이 [승인 테스트 러닝]에 참여를 신청했습니다.",
                NotificationActionType.APPROVE_OR_REJECT, NotificationActionStatus.RESOLVED, session.getId(), participation.getId());
        assertNotification(notification(notifications, NotificationType.PARTICIPATION_APPROVED),
                NotificationType.PARTICIPATION_APPROVED, applicant.getId(), host.getId(), host.getName(),
                "참여가 확정되었습니다!", "[승인 테스트 러닝] 참여 신청이 승인되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE, session.getId(), participation.getId());
    }

    @Test
    void 신청_후_거절하면_요청을_해결하고_정확한_거절_알림을_저장한다() {
        User host = user("거절 호스트");
        User applicant = user("거절 신청자");
        Session session = session(host, "거절 테스트 러닝");

        SessionParticipant participation = join(host, applicant, session);
        sessionService.rejectJoinRequest(host.getId(), session.getId(), participation.getId());

        List<Notification> notifications = reloadedNotifications(session.getId());

        assertThat(notifications).hasSize(2);
        assertThat(notification(notifications, NotificationType.PARTICIPATION_REQUESTED).getActionStatus())
                .isEqualTo(NotificationActionStatus.RESOLVED);
        assertNotification(notification(notifications, NotificationType.PARTICIPATION_REJECTED),
                NotificationType.PARTICIPATION_REJECTED, applicant.getId(), host.getId(), host.getName(),
                "참여 신청이 거절되었습니다.", "[거절 테스트 러닝] 참여 신청이 거절되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE, session.getId(), participation.getId());
    }

    @Test
    void 신청_후_강퇴하면_정확한_강퇴_알림을_저장한다() {
        User host = user("강퇴 호스트");
        User applicant = user("강퇴 신청자");
        Session session = session(host, "강퇴 테스트 러닝");

        SessionParticipant participation = join(host, applicant, session);
        sessionService.startSession(host.getId(), session.getId());
        sessionService.kickParticipant(host.getId(), session.getId(), participation.getId());

        List<Notification> notifications = reloadedNotifications(session.getId());

        assertThat(notifications).hasSize(2);
        assertThat(notification(notifications, NotificationType.PARTICIPATION_REQUESTED).getActionStatus())
                .isEqualTo(NotificationActionStatus.PENDING);
        assertNotification(notification(notifications, NotificationType.PARTICIPANT_KICKED),
                NotificationType.PARTICIPANT_KICKED, applicant.getId(), host.getId(), host.getName(),
                "세션 참여가 취소되었습니다.", "[강퇴 테스트 러닝] 참여자 명단에서 제외되었습니다.",
                NotificationActionType.NAVIGATE, NotificationActionStatus.NONE, session.getId(), participation.getId());
    }

    @Test
    void 알림_저장에_실패하면_참여_신청도_같은_트랜잭션에서_롤백된다() {
        User host = user("롤백 호스트");
        User applicant = user("롤백 신청자");
        Session session = session(host, "롤백 테스트 러닝");
        Long nextParticipationId = nextParticipationId();
        Notification duplicate = notificationRepository.saveAndFlush(Notification.builder()
                .recipientUserId(host.getId())
                .type(NotificationType.PARTICIPATION_REQUESTED)
                .title("기존 요청")
                .body("기존 요청")
                .sessionId(session.getId())
                .participationId(nextParticipationId)
                .actionType(NotificationActionType.APPROVE_OR_REJECT)
                .actionStatus(NotificationActionStatus.PENDING)
                .deduplicationKey(NotificationType.PARTICIPATION_REQUESTED + ":" + nextParticipationId + ":" + host.getId())
                .build());
        taskNotificationIds.add(duplicate.getId());

        assertThatThrownBy(() -> sessionService.joinSession(
                applicant.getId(), session.getId(), new SessionJoinRequest("함께 달리고 싶어요.")))
                .isInstanceOf(DataIntegrityViolationException.class);

        entityManager.clear();
        assertThat(sessionParticipantRepository.findBySessionId(session.getId())).isEmpty();
    }

    private User user(String name) {
        long sequence = USER_SEQUENCE.incrementAndGet();
        User user = userRepository.saveAndFlush(User.builder()
                .username("session-notification-" + sequence)
                .password("password")
                .name(name)
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build());
        taskUserIds.add(user.getId());
        return user;
    }

    private Session session(User host, String title) {
        Session session = sessionRepository.saveAndFlush(Session.builder()
                .hostUser(host)
                .title(title)
                .runType(RunType.RECOVERY)
                .locationName("테스트 장소")
                .location(GeometryUtil.createPoint(BigDecimal.ZERO, BigDecimal.ZERO))
                .routePolyline(List.of(new Session.RoutePoint(BigDecimal.ZERO, BigDecimal.ZERO)))
                .targetDistanceKm(BigDecimal.ONE)
                .avgPaceSec(360)
                .startAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .capacity(2)
                .genderPolicy(GenderPolicy.MIXED)
                .status(SessionStatus.OPEN)
                .build());
        taskSessionIds.add(session.getId());
        return session;
    }

    private SessionParticipant join(User host, User applicant, Session session) {
        sessionService.joinSession(applicant.getId(), session.getId(), new SessionJoinRequest("함께 달리고 싶어요."));
        SessionParticipant participation = sessionParticipantRepository.findBySessionId(session.getId()).stream()
                .findFirst()
                .orElseThrow();
        taskParticipantIds.add(participation.getId());
        return participation;
    }

    private void createGroupRoom(Session session) {
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.group(session));
        chatRoomMemberRepository.saveAndFlush(
                ChatRoomMember.join(room, session.getHostUser(), ChatMemberRole.HOST));
        taskChatRoomIds.add(room.getId());
    }

    private void deleteTaskOwnedChatRows() {
        List<Long> messageIds = chatMessageRepository.findAll().stream()
                .filter(message -> taskChatRoomIds.contains(message.getRoom().getId()))
                .map(message -> message.getId())
                .toList();
        chatOutboxRepository.deleteAll(chatOutboxRepository.findAll().stream()
                .filter(outbox -> messageIds.contains(outbox.getAggregateId()))
                .toList());
        chatMessageRepository.deleteAllById(messageIds);
        chatRoomMemberRepository.deleteAllById(chatRoomMemberRepository.findAll().stream()
                .filter(member -> taskChatRoomIds.contains(member.getRoom().getId()))
                .map(member -> member.getId())
                .toList());
        chatRoomRepository.deleteAllById(taskChatRoomIds);
        taskChatRoomIds.clear();
    }

    private List<Notification> reloadedNotifications(Long sessionId) {
        entityManager.clear();
        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(notification -> notification.getSessionId().equals(sessionId))
                .toList();
        taskNotificationIds.addAll(notifications.stream().map(Notification::getId).toList());
        return notifications;
    }

    private Long nextParticipationId() {
        return jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'session_participants'",
                Long.class
        );
    }

    private Notification notification(List<Notification> notifications, NotificationType type) {
        return notifications.stream()
                .filter(notification -> notification.getType() == type)
                .findFirst()
                .orElseThrow();
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
            NotificationActionStatus actionStatus,
            Long sessionId,
            Long participationId
    ) {
        assertThat(notification.getType()).isEqualTo(type);
        assertThat(notification.getRecipientUserId()).isEqualTo(recipientUserId);
        assertThat(notification.getActorUserId()).isEqualTo(actorUserId);
        assertThat(notification.getActorName()).isEqualTo(actorName);
        assertThat(notification.getActorProfileImageUrl()).isNull();
        assertThat(notification.getTitle()).isEqualTo(title);
        assertThat(notification.getBody()).isEqualTo(body);
        assertThat(notification.getSessionId()).isEqualTo(sessionId);
        assertThat(notification.getParticipationId()).isEqualTo(participationId);
        assertThat(notification.getActionType()).isEqualTo(actionType);
        assertThat(notification.getActionStatus()).isEqualTo(actionStatus);
        assertThat(notification.getDeduplicationKey()).isEqualTo(type + ":" + participationId + ":" + recipientUserId);
    }
}
