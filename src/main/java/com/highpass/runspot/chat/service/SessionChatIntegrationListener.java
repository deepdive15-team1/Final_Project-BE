package com.highpass.runspot.chat.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.dto.ChatMessageResponse;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.outbox.ChatOutboxService;
import com.highpass.runspot.chat.repository.*;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.dao.SessionRepository;
import com.highpass.runspot.session.event.*;

import lombok.RequiredArgsConstructor;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionChatIntegrationListener {
    private final SessionRepository sessions;
    private final UserRepository users;
    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository members;
    private final ChatMessageRepository messages;
    private final ChatOutboxService outbox;

    @EventListener
    public void sessionCreated(SessionCreatedEvent event) {
        Session session =
                sessions.findById(event.sessionId())
                        .orElseThrow(() -> error(ChatErrorCode.SESSION_NOT_FOUND));
        if (rooms.findBySessionIdAndRoomType(session.getId(), ChatRoomType.GROUP).isPresent()) {
            return;
        }
        ChatRoom room = rooms.saveAndFlush(ChatRoom.group(session));
        members.save(ChatRoomMember.join(room, session.getHostUser(), ChatMemberRole.HOST));
    }

    @EventListener
    public void participantApproved(ParticipantApprovedEvent event) {
        ChatRoom room =
                rooms.findBySessionIdAndRoomType(event.sessionId(), ChatRoomType.GROUP)
                        .orElseThrow(() -> error(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
        if (members.existsByRoomIdAndUserIdAndLeftAtIsNull(room.getId(), event.userId())) {
            return;
        }
        User user =
                users.findById(event.userId())
                        .orElseThrow(() -> error(ChatErrorCode.USER_NOT_FOUND));
        members.save(ChatRoomMember.join(room, user, ChatMemberRole.MEMBER));

        ChatMessage joinNotice =
                messages.saveAndFlush(ChatMessage.system(room, user.getName() + "님이 참여했습니다."));
        room.updateLastMessage(joinNotice.getId(), joinNotice.getCreatedAt());
        outbox.enqueue(joinNotice.getId(), room.getId(), ChatMessageResponse.from(joinNotice));
    }

    private ChatException error(ChatErrorCode code) {
        return new ChatException(code);
    }
}
