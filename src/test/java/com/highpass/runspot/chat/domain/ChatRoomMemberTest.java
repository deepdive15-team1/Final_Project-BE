package com.highpass.runspot.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.highpass.runspot.auth.domain.User;

import org.junit.jupiter.api.Test;

class ChatRoomMemberTest {
    @Test
    void 읽음_커서는_과거_메시지로_되돌아가지_않는다() {
        ChatRoomMember member =
                ChatRoomMember.join(
                        org.mockito.Mockito.mock(ChatRoom.class),
                        User.builder().id(1L).build(),
                        ChatMemberRole.MEMBER);
        member.read(100L);
        member.read(50L);
        assertThat(member.getLastReadMessageId()).isEqualTo(100L);
    }
}
