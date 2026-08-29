package com.highpass.runspot.chat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.dto.*;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.repository.*;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.dao.SessionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {
    @Mock ChatRoomRepository rooms;
    @Mock ChatRoomMemberRepository members;
    @Mock ChatMessageRepository messages;
    @Mock SessionRepository sessions;
    @Mock UserRepository users;
    @InjectMocks ChatRoomService service;

    @Test
    void 기존_문의방이_있으면_같은_방을_반환한다() {
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        when(room.getId()).thenReturn(7L);
        when(rooms.findBySessionIdAndGuestIdAndRoomType(10L, 2L, ChatRoomType.DIRECT))
                .thenReturn(Optional.of(room));
        DirectRoomResponse response = service.direct(2L, new DirectRoomRequest(10L));
        assertThat(response.roomId()).isEqualTo(7L);
        assertThat(response.created()).isFalse();
    }

    @Test
    void 호스트는_자신에게_문의방을_만들_수_없다() {
        User host = User.builder().id(1L).build();
        Session session = Session.builder().id(10L).hostUser(host).build();
        when(rooms.findBySessionIdAndGuestIdAndRoomType(10L, 1L, ChatRoomType.DIRECT))
                .thenReturn(Optional.empty());
        when(sessions.findById(10L)).thenReturn(Optional.of(session));
        assertThatThrownBy(() -> service.direct(1L, new DirectRoomRequest(10L)))
                .isInstanceOf(ChatException.class)
                .hasFieldOrPropertyWithValue(
                        "exceptionType", ChatErrorCode.HOST_DIRECT_ROOM_NOT_ALLOWED);
    }
}
