package com.highpass.runspot.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SessionParticipantTest {

    @Test
    void 요청된_참여자를_승인하면_APPROVED가_된다() {
        SessionParticipant participant = participant(ParticipationStatus.REQUESTED);

        participant.approve();

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.APPROVED);
    }

    @Test
    void 요청된_참여자를_거절하면_REJECTED가_된다() {
        SessionParticipant participant = participant(ParticipationStatus.REQUESTED);

        participant.reject();

        assertThat(participant.getStatus()).isEqualTo(ParticipationStatus.REJECTED);
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    void 요청_상태가_아닌_참여자는_승인할_수_없다(ParticipationStatus status) {
        SessionParticipant participant = participant(status);

        assertThatThrownBy(participant::approve)
                .isInstanceOf(IllegalStateException.class);

        assertThat(participant.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = ParticipationStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    void 요청_상태가_아닌_참여자는_거절할_수_없다(ParticipationStatus status) {
        SessionParticipant participant = participant(status);

        assertThatThrownBy(participant::reject)
                .isInstanceOf(IllegalStateException.class);

        assertThat(participant.getStatus()).isEqualTo(status);
    }

    private SessionParticipant participant(ParticipationStatus status) {
        return SessionParticipant.builder()
                .status(status)
                .build();
    }
}
