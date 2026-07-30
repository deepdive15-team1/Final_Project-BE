package com.highpass.runspot.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.highpass.runspot.auth.domain.User;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final Long HOST_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private Session sessionWithStatus(SessionStatus status) {
        User host = User.builder().id(HOST_ID).build();
        return Session.builder()
                .id(100L)
                .hostUser(host)
                .status(status)
                .build();
    }

    @Test
    void 마감된_세션을_호스트가_시작하면_IN_PROGRESS_상태가_된다() {
        Session session = sessionWithStatus(SessionStatus.CLOSED);

        session.start(HOST_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void 모집중인_세션도_호스트가_시작할_수_있다() {
        Session session = sessionWithStatus(SessionStatus.OPEN);

        session.start(HOST_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void 호스트가_아니면_세션을_시작할_수_없다() {
        Session session = sessionWithStatus(SessionStatus.CLOSED);

        assertThatThrownBy(() -> session.start(OTHER_USER_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    void 이미_진행중인_세션은_다시_시작할_수_없다() {
        Session session = sessionWithStatus(SessionStatus.IN_PROGRESS);

        assertThatThrownBy(() -> session.start(HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 이미_종료된_세션은_다시_시작할_수_없다() {
        Session session = sessionWithStatus(SessionStatus.FINISHED);

        assertThatThrownBy(() -> session.start(HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 취소된_세션은_시작할_수_없다() {
        Session session = sessionWithStatus(SessionStatus.CANCELED);

        assertThatThrownBy(() -> session.start(HOST_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 진행중인_세션을_호스트가_종료하면_FINISHED_상태가_된다() {
        Session session = sessionWithStatus(SessionStatus.IN_PROGRESS);

        session.finish(HOST_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    void 시작하지_않은_세션은_종료할_수_없다() {
        Session session = sessionWithStatus(SessionStatus.CLOSED);

        assertThatThrownBy(() -> session.finish(HOST_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    void 호스트가_아니면_세션을_종료할_수_없다() {
        Session session = sessionWithStatus(SessionStatus.IN_PROGRESS);

        assertThatThrownBy(() -> session.finish(OTHER_USER_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
    }
}
