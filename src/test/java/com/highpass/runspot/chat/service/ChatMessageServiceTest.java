package com.highpass.runspot.chat.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.dto.ChatSendRequest;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.repository.*;
import com.highpass.runspot.file.service.S3PresignService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {
    @Mock ChatRoomRepository rooms;
    @Mock ChatRoomMemberRepository members;
    @Mock ChatMessageRepository messages;
    @Mock UserRepository users;
    @Mock S3PresignService files;
    @InjectMocks ChatMessageService service;

    private ChatSendRequest request() {
        return new ChatSendRequest(10L, ChatMessageType.TEXT, "안녕하세요", List.of(), "client-1");
    }

    @Test
    void 채팅방_멤버가_아니면_메시지를_보낼_수_없다() {
        when(members.findByRoomIdAndUserIdAndLeftAtIsNull(10L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(2L, request()))
                .isInstanceOf(ChatException.class)
                .hasFieldOrPropertyWithValue("exceptionType", ChatErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void 종료된_방에는_메시지를_보낼_수_없다() {
        ChatRoomMember member = org.mockito.Mockito.mock(ChatRoomMember.class);
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        when(members.findByRoomIdAndUserIdAndLeftAtIsNull(10L, 2L)).thenReturn(Optional.of(member));
        when(rooms.findDetail(10L)).thenReturn(Optional.of(room));
        when(room.getStatus()).thenReturn(ChatRoomStatus.DELETED);
        assertThatThrownBy(() -> service.send(2L, request()))
                .isInstanceOf(ChatException.class)
                .hasFieldOrPropertyWithValue("exceptionType", ChatErrorCode.CHAT_ROOM_CLOSED);
    }
}
