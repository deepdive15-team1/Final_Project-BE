package com.highpass.runspot.notification.service;

import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository;
import com.highpass.runspot.session.domain.dao.SessionParticipantRepository.ApprovedReminderRecipient;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationReminderScheduler {

    private static final int MYSQL_DUPLICATE_KEY_ERROR = 1062;
    private static final String DEDUPLICATION_CONSTRAINT = "uk_notifications_deduplication_key";

    private final Clock clock;
    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;
    private final NotificationReminderCreator creator;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void sendSessionStartReminders() {
        LocalDateTime currentMinute = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime startInclusive = currentMinute.plusMinutes(30);
        LocalDateTime endExclusive = currentMinute.plusMinutes(31);
        List<Session> candidates = sessionRepository.findStartReminderCandidates(startInclusive, endExclusive);
        if (candidates.isEmpty()) {
            return;
        }

        Map<Long, Set<Long>> recipientIdsBySession = recipientsBySession(candidates);
        for (Session candidate : candidates) {
            for (Long recipientId : recipientIdsBySession.get(candidate.getId())) {
                createIgnoringDuplicate(candidate, recipientId);
            }
        }
    }

    private Map<Long, Set<Long>> recipientsBySession(List<Session> candidates) {
        Map<Long, Set<Long>> recipientIdsBySession = new LinkedHashMap<>();
        for (Session candidate : candidates) {
            recipientIdsBySession.put(candidate.getId(), new LinkedHashSet<>(List.of(candidate.getHostUser().getId())));
        }

        List<Long> sessionIds = candidates.stream().map(Session::getId).toList();
        for (ApprovedReminderRecipient recipient :
                sessionParticipantRepository.findApprovedReminderRecipients(sessionIds)) {
            recipientIdsBySession.get(recipient.getSessionId()).add(recipient.getUserId());
        }
        return recipientIdsBySession;
    }

    private void createIgnoringDuplicate(Session session, Long recipientId) {
        try {
            creator.createReminder(session.getId(), session.getTitle(), recipientId);
        } catch (DataIntegrityViolationException exception) {
            if (!isExpectedDuplicate(exception)) {
                throw exception;
            }
        }
    }

    private boolean isExpectedDuplicate(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR
                    && sqlException.getMessage().contains(DEDUPLICATION_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
