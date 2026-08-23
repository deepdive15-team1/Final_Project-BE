package com.highpass.runspot.notification.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.notification.domain.Notification;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.SessionStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class NotificationReminderConcurrencyTest extends NotificationReminderIntegrationSupport {

    private static final int WAIT_SECONDS = 10;
    private ExecutorService executor;

    @BeforeEach
    void createExecutor() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(WAIT_SECONDS, SECONDS)).isTrue();
    }

    @Test
    @Timeout(60)
    void 순차_두번과_동시_두번_실행해도_수신자별_한_행만_남는다() throws Exception {
        User host = user("호스트");
        User approved = user("승인");
        Session sequentialSession = session(host, FIXED_NOW.plusMinutes(30), SessionStatus.OPEN);
        participant(sequentialSession, approved, ParticipationStatus.APPROVED);

        scheduler.sendSessionStartReminders();
        scheduler.sendSessionStartReminders();

        Session parallelSession = session(host, FIXED_NOW.plusMinutes(30).plusSeconds(30), SessionStatus.CLOSED);
        participant(parallelSession, approved, ParticipationStatus.APPROVED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> runWhenStarted(ready, start));
        Future<?> second = executor.submit(() -> runWhenStarted(ready, start));

        assertThat(ready.await(WAIT_SECONDS, SECONDS)).isTrue();
        start.countDown();
        first.get(WAIT_SECONDS, SECONDS);
        second.get(WAIT_SECONDS, SECONDS);

        assertOneReminderPerRecipient(sequentialSession, host, approved);
        assertOneReminderPerRecipient(parallelSession, host, approved);
    }

    @Test
    void 기존_중복_한_건이_있어도_다음_수신자_리마인더는_독립적으로_커밋된다() {
        User host = user("호스트");
        User approved = user("승인");
        Session session = session(host, FIXED_NOW.plusMinutes(30), SessionStatus.OPEN);
        participant(session, approved, ParticipationStatus.APPROVED);
        creator.createReminder(session.getId(), session.getTitle(), host.getId());

        scheduler.sendSessionStartReminders();

        assertOneReminderPerRecipient(session, host, approved);
    }

    private void runWhenStarted(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(WAIT_SECONDS, SECONDS)) {
                throw new IllegalStateException("동시 리마인더 시작 신호 대기 시간이 초과되었습니다.");
            }
            scheduler.sendSessionStartReminders();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 리마인더 실행이 중단되었습니다.", exception);
        }
    }

    private void assertOneReminderPerRecipient(Session session, User host, User approved) {
        List<Notification> reminders = notificationRepository.findAll().stream()
                .filter(notification -> notification.getSessionId().equals(session.getId()))
                .toList();
        assertThat(reminders).hasSize(2);
        assertThat(reminders).extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(host.getId(), approved.getId());
        assertThat(reminders).extracting(Notification::getDeduplicationKey).doesNotHaveDuplicates();
    }
}
