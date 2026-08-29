package com.highpass.runspot.chat.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.dto.*;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.outbox.ChatOutboxService;
import com.highpass.runspot.chat.repository.*;
import com.highpass.runspot.file.service.S3PresignService;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository members;
    private final ChatMessageRepository messages;
    private final UserRepository users;
    private final S3PresignService files;
    private final ChatOutboxService outbox;

    @Transactional
    public ChatSendResponse send(Long userId, ChatSendRequest request) {
        ChatRoomMember member =
                members.findByRoomIdAndUserIdAndLeftAtIsNull(request.roomId(), userId)
                        .orElseThrow(() -> error(ChatErrorCode.NOT_ROOM_MEMBER));
        ChatRoom room =
                rooms.findDetail(request.roomId())
                        .orElseThrow(() -> error(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
        if (room.getStatus() != ChatRoomStatus.ACTIVE) {
            throw error(ChatErrorCode.CHAT_ROOM_CLOSED);
        }
        boolean isHost = member.getRole() == ChatMemberRole.HOST;
        Optional<ChatMessage> duplicate = findDuplicate(request);
        if (duplicate.isPresent()) {
            return ChatSendResponse.from(duplicate.get(), isHost);
        }
        validate(request);
        User sender = users.findById(userId).orElseThrow(() -> error(ChatErrorCode.USER_NOT_FOUND));
        files.link(userId, request.imageKeys());

        ChatMessage saved;
        try {
            saved = messages.saveAndFlush(ChatMessage.create(
                    room,
                    sender,
                    request.clientMessageId(),
                    request.messageType(),
                    request.content(),
                    request.imageKeys()));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return findDuplicate(request)
                    .map(existing -> ChatSendResponse.from(existing, isHost))
                    .orElseThrow(() -> concurrentDuplicate);
        }

        room.updateLastMessage(saved.getId(), saved.getCreatedAt());
        ChatSendResponse response = ChatSendResponse.from(saved, isHost);
        outbox.enqueue(saved.getId(), room.getId(), response);
        return response;
    }

    private Optional<ChatMessage> findDuplicate(ChatSendRequest request) {
        return messages.findByRoomIdAndClientMessageId(request.roomId(), request.clientMessageId());
    }

    private void validate(ChatSendRequest request) {
        boolean blankText =
                request.messageType() == ChatMessageType.TEXT
                        && (request.content() == null || request.content().isBlank());
        boolean missingImages =
                request.messageType() == ChatMessageType.IMAGE && request.imageKeys().isEmpty();
        boolean systemMessage = request.messageType() == ChatMessageType.SYSTEM;
        if (blankText || missingImages || systemMessage) {
            throw error(ChatErrorCode.INVALID_CHAT_MESSAGE);
        }
    }

    private ChatException error(ChatErrorCode code) {
        return new ChatException(code);
    }
}
