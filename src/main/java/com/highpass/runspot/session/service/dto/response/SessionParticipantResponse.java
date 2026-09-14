package com.highpass.runspot.session.service.dto.response;

import com.highpass.runspot.auth.domain.AgeGroup;
import com.highpass.runspot.auth.domain.Gender;
import com.highpass.runspot.session.domain.AttendanceStatus;
import com.highpass.runspot.session.domain.ParticipationStatus;
import com.highpass.runspot.session.domain.SessionParticipant;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionParticipantResponse(
    Long id,
    Long userId,
    String userName,
    Gender userGender,
    AgeGroup userAgeGroup,
    Integer userPacePreferenceSec,
    BigDecimal userMannerTemp,
    ParticipationStatus status,
    AttendanceStatus attendanceStatus,
    String messageToHost,
    LocalDateTime requestedAt
) {
    public static SessionParticipantResponse from(SessionParticipant participant) {
        return new SessionParticipantResponse(
            participant.getId(),
            participant.getUser().getId(),
            participant.getUser().getName(),
            participant.getUser().getGender(),
            participant.getUser().getAgeGroup(),
            participant.getUser().getPacePreferenceSec(),
            participant.getUser().getMannerTemp(),
            participant.getStatus(),
            participant.getAttendanceStatus(),
            participant.getMessageToHost(),
            participant.getCreatedAt()
        );
    }
}
