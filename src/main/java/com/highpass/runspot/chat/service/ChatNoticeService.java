package com.highpass.runspot.chat.service;

import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatNoticeService {
    private final ChatRoomMemberRepository members;
    private final ChatNoticeRepository notices;

    @Transactional
    public void upsert(Long userId, Long roomId, String content) {
        ChatRoomMember host = requireHost(userId, roomId);
        ChatNotice activeNotice = notices.findByRoomIdAndActiveTrue(roomId).orElse(null);
        if (activeNotice == null) {
            notices.save(ChatNotice.create(host.getRoom(), host.getUser(), content));
        } else {
            activeNotice.update(content);
        }
    }

    @Transactional
    public void delete(Long userId, Long roomId) {
        requireHost(userId, roomId);
        notices.findByRoomIdAndActiveTrue(roomId).ifPresent(ChatNotice::deactivate);
    }

    private ChatRoomMember requireHost(Long userId, Long roomId) {
        ChatRoomMember member =
                members.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
                        .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_ROOM_MEMBER));
        if (member.getRole() != ChatMemberRole.HOST) {
            throw new ChatException(ChatErrorCode.NOT_ROOM_HOST);
        }
        return member;
    }
}
