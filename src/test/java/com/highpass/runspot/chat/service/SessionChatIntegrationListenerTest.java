package com.highpass.runspot.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.outbox.ChatOutboxService;
import com.highpass.runspot.chat.repository.*;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.session.event.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class SessionChatIntegrationListenerTest {
    @Mock SessionRepository sessions;
    @Mock UserRepository users;
    @Mock ChatRoomRepository rooms;
    @Mock ChatRoomMemberRepository members;
    @Mock ChatMessageRepository messages;
    @Mock ChatOutboxService outbox;
    @InjectMocks SessionChatIntegrationListener listener;

    @Test
    void 세션이_생성되면_호스트가_포함된_그룹방을_생성한다() {
        User host = User.builder().id(1L).build();
        Session session = Session.builder().id(10L).hostUser(host).title("러닝").build();
        ChatRoom room = mock(ChatRoom.class);
        when(sessions.findById(10L)).thenReturn(Optional.of(session));
        when(rooms.findBySessionIdAndRoomType(10L, ChatRoomType.GROUP))
                .thenReturn(Optional.empty());
        when(rooms.saveAndFlush(any(ChatRoom.class))).thenReturn(room);
        listener.sessionCreated(new SessionCreatedEvent(10L));
        verify(members).save(any(ChatRoomMember.class));
    }

    @Test
    void 참여가_승인되면_그룹방_멤버와_시스템메시지를_생성한다() {
        ChatRoom room = mock(ChatRoom.class);
        ChatMessage message = mock(ChatMessage.class);
        User user = User.builder().id(2L).name("러너").build();
        when(room.getId()).thenReturn(20L);
        when(rooms.findBySessionIdAndRoomType(10L, ChatRoomType.GROUP))
                .thenReturn(Optional.of(room));
        when(users.findById(2L)).thenReturn(Optional.of(user));
        when(messages.saveAndFlush(any(ChatMessage.class))).thenReturn(message);
        listener.participantApproved(new ParticipantApprovedEvent(10L, 2L));
        verify(members).save(any(ChatRoomMember.class));
        verify(messages).saveAndFlush(any(ChatMessage.class));
    }
}
