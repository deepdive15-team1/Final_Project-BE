package com.highpass.runspot.notification.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.auth.domain.RefreshToken;
import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.RefreshTokenRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.service.AuthService;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.notification.domain.NotificationActionStatus;
import com.highpass.runspot.notification.domain.NotificationActionType;
import com.highpass.runspot.notification.domain.NotificationType;
import com.highpass.runspot.notification.domain.dao.NotificationRepository;
import com.highpass.runspot.notification.push.domain.PushPlatform;
import com.highpass.runspot.notification.push.domain.dao.PushDeviceTokenRepository;
import com.highpass.runspot.notification.push.outbox.PushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService;
import com.highpass.runspot.notification.push.outbox.PushOutboxClaimService.ClaimedPushOutbox;
import com.highpass.runspot.notification.push.outbox.PushOutboxFinalizeService;
import com.highpass.runspot.notification.push.outbox.PushOutboxRepository;
import com.highpass.runspot.notification.push.outbox.PushOutboxStatus;
import com.highpass.runspot.support.MySqlContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles("test")
@SpringBootTest
class PushUserCleanupTest extends MySqlContainerSupport {

    private static final Long TARGET_USER_ID = 8101L;
    private static final Long OTHER_USER_ID = 8102L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);
    private static final String DEDUPLICATION_PREFIX = "push-user-cleanup:";

    @Autowired private AuthService authService;
    @Autowired private PushDeviceTokenRepository pushDeviceTokenRepository;
    @Autowired private PushOutboxRepository pushOutboxRepository;
    @Autowired private PushOutboxClaimService claimService;
    @Autowired private PushOutboxFinalizeService finalizeService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private UserRepository userRepository;
    @MockitoSpyBean private PushDeviceTokenService pushDeviceTokenService;
    @PersistenceContext private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        cleanTaskData();
    }

    @AfterEach
    void cleanTaskData() {
        pushOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
        pushDeviceTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void revokeDeletesClaimableRowsRetainsTerminalRowsAndMakesStaleFinalizeANoOp() {
        pushDeviceTokenService.upsert(TARGET_USER_ID, "target-token", PushPlatform.ANDROID);
        ClaimedPushOutbox processing = persistRowsForRecipient(TARGET_USER_ID, OTHER_USER_ID, "revoke");
        PushOutbox unrelated = persistOutbox(OTHER_USER_ID, OTHER_USER_ID, "revoke-unrelated");

        pushDeviceTokenService.delete(TARGET_USER_ID);

        assertThat(pushDeviceTokenRepository.findByUserId(TARGET_USER_ID)).isEmpty();
        assertThat(pushOutboxRepository.findAll())
                .extracting(PushOutbox::getStatus)
                .containsExactlyInAnyOrder(PushOutboxStatus.SENT, PushOutboxStatus.FAILED, PushOutboxStatus.PENDING);
        assertThat(pushOutboxRepository.findById(unrelated.getId())).isPresent();
        assertThat(finalizeService.markSent(processing, NOW.plusSeconds(1))).isFalse();

        pushDeviceTokenService.delete(TARGET_USER_ID);
    }

    @Test
    void logoutUsesStoredTokenOwnerAndRetainsTerminalOutboxRows() {
        pushDeviceTokenService.upsert(TARGET_USER_ID, "logout-token", PushPlatform.ANDROID);
        persistRowsForRecipient(TARGET_USER_ID, OTHER_USER_ID, "logout");
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                TARGET_USER_ID,
                "logout-refresh-token",
                NOW.plusDays(1)
        ));

        authService.logout("logout-refresh-token");

        assertThat(refreshTokenRepository.findByToken("logout-refresh-token")).isEmpty();
        assertThat(pushDeviceTokenRepository.findByUserId(TARGET_USER_ID)).isEmpty();
        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getStatus)
                .containsExactlyInAnyOrder(PushOutboxStatus.SENT, PushOutboxStatus.FAILED);
    }

    @Test
    void logoutWithoutRefreshTokenIsIdempotentAndDoesNotInferAnOwner() {
        pushDeviceTokenService.upsert(TARGET_USER_ID, "unrelated-token", PushPlatform.ANDROID);
        PushOutbox outbox = persistOutbox(TARGET_USER_ID, OTHER_USER_ID, "missing-logout");

        authService.logout("missing-refresh-token");

        assertThat(pushDeviceTokenRepository.findByUserId(TARGET_USER_ID)).isPresent();
        assertThat(pushOutboxRepository.findById(outbox.getId())).isPresent();
    }

    @Test
    void withdrawalDeletesEveryOutboxRelatedToTheUserBeforeItsNotifications() {
        User target = user("withdraw-target");
        User other = user("withdraw-other");
        pushDeviceTokenService.upsert(target.getId(), "withdraw-token", PushPlatform.ANDROID);
        persistRowsForWithdrawal(target.getId(), other.getId());
        PushOutbox unrelated = persistOutbox(other.getId(), other.getId(), "withdraw-unrelated");

        authService.withdraw(target.getId());
        entityManager.clear();

        verify(pushDeviceTokenService).delete(target.getId());
        assertThat(userRepository.findById(target.getId())).isEmpty();
        assertThat(pushDeviceTokenRepository.findByUserId(target.getId())).isEmpty();
        assertThat(notificationRepository.findAll()).extracting(Notification::getRecipientUserId)
                .containsExactly(other.getId());
        assertThat(pushOutboxRepository.findAll()).extracting(PushOutbox::getId)
                .containsExactly(unrelated.getId());
    }

    @Test
    void logoutRollbackRestoresOutboxTokenAndRefreshTokenAfterCleanupFailure() {
        pushDeviceTokenService.upsert(TARGET_USER_ID, "rollback-token", PushPlatform.ANDROID);
        PushOutbox outbox = persistOutbox(TARGET_USER_ID, OTHER_USER_ID, "rollback");
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                TARGET_USER_ID,
                "rollback-refresh-token",
                NOW.plusDays(1)
        ));
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("force lifecycle rollback");
        }).when(pushDeviceTokenService).delete(eq(TARGET_USER_ID));

        assertThatThrownBy(() -> authService.logout("rollback-refresh-token"))
                .isInstanceOf(IllegalStateException.class);
        entityManager.clear();

        assertThat(pushDeviceTokenRepository.findByUserId(TARGET_USER_ID)).isPresent();
        assertThat(pushOutboxRepository.findById(outbox.getId())).isPresent();
        assertThat(refreshTokenRepository.findByToken("rollback-refresh-token")).isPresent();
    }

    @Test
    void staleUnregisteredFailureCannotDeleteAReplacementOrAnotherUsersToken() {
        pushDeviceTokenService.upsert(TARGET_USER_ID, "stale-token", PushPlatform.ANDROID);
        PushOutbox outbox = persistOutbox(TARGET_USER_ID, OTHER_USER_ID, "stale-token");
        ClaimedPushOutbox claim = claimService.claim(1, NOW).get(0);
        pushDeviceTokenService.upsert(OTHER_USER_ID, "stale-token", PushPlatform.ANDROID);
        pushDeviceTokenService.upsert(TARGET_USER_ID, "replacement-token", PushPlatform.ANDROID);

        assertThat(finalizeService.markFailedAndDeleteMatchingToken(
                claim,
                NOW.plusSeconds(1),
                "UNREGISTERED",
                "stale-token",
                true
        )).isTrue();

        assertThat(pushDeviceTokenRepository.findByUserId(TARGET_USER_ID).orElseThrow().getToken())
                .isEqualTo("replacement-token");
        assertThat(pushDeviceTokenRepository.findByUserId(OTHER_USER_ID).orElseThrow().getToken())
                .isEqualTo("stale-token");
        assertThat(pushOutboxRepository.findById(outbox.getId()).orElseThrow().getStatus())
                .isEqualTo(PushOutboxStatus.FAILED);
    }

    private ClaimedPushOutbox persistRowsForRecipient(Long recipientUserId, Long actorUserId, String suffix) {
        persistOutbox(recipientUserId, actorUserId, suffix + "-processing");
        persistOutbox(recipientUserId, actorUserId, suffix + "-sent");
        persistOutbox(recipientUserId, actorUserId, suffix + "-failed");
        persistOutbox(recipientUserId, actorUserId, suffix + "-pending");
        List<ClaimedPushOutbox> claims = claimService.claim(3, NOW);
        finalizeService.markSent(claims.get(1), NOW);
        finalizeService.markFailed(claims.get(2), NOW, "INVALID_ARGUMENT");
        return claims.get(0);
    }

    private void persistRowsForWithdrawal(Long targetUserId, Long otherUserId) {
        persistOutbox(targetUserId, otherUserId, "withdraw-processing");
        persistOutbox(otherUserId, targetUserId, "withdraw-sent");
        persistOutbox(targetUserId, targetUserId, "withdraw-failed");
        persistOutbox(otherUserId, targetUserId, "withdraw-pending");
        List<ClaimedPushOutbox> claims = claimService.claim(3, NOW);
        finalizeService.markSent(claims.get(1), NOW);
        finalizeService.markFailed(claims.get(2), NOW, "INVALID_ARGUMENT");
    }

    private PushOutbox persistOutbox(Long recipientUserId, Long actorUserId, String suffix) {
        Notification notification = notificationRepository.saveAndFlush(Notification.builder()
                .recipientUserId(recipientUserId)
                .actorUserId(actorUserId)
                .actorName("푸시 정리 행위자")
                .type(NotificationType.PARTICIPATION_APPROVED)
                .title("푸시 정리 알림")
                .body("푸시 정리 본문")
                .sessionId(1L)
                .participationId(1L)
                .actionType(NotificationActionType.NAVIGATE)
                .actionStatus(NotificationActionStatus.NONE)
                .deduplicationKey(DEDUPLICATION_PREFIX + suffix)
                .build());
        return pushOutboxRepository.saveAndFlush(PushOutbox.pending(notification, NOW));
    }

    private User user(String suffix) {
        return userRepository.saveAndFlush(User.builder()
                .username(DEDUPLICATION_PREFIX + suffix)
                .password("password")
                .name("푸시 정리 사용자")
                .ageGroup(AgeGroup.TWENTIES)
                .gender(Gender.MALE)
                .build());
    }
}
