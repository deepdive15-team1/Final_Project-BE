package com.highpass.runspot.chat.service;

import com.highpass.runspot.chat.domain.ChatRoomMember;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ChatReadService {
    private final ChatRoomMemberRepository members;
    private final ChatMessageRepository messages;
    private final StringRedisTemplate redis;

    public void incrementForRecipients(Long roomId, Long senderId) {
        for (ChatRoomMember member : members.findByRoomIdAndLeftAtIsNull(roomId)) {
            if (!member.getUser().getId().equals(senderId)) {
                bestEffortWrite(
                        () -> redis.opsForHash()
                                .increment(key(member.getUser().getId()), roomId.toString(), 1));
            }
        }
    }

    public long unread(Long userId, ChatRoomMember member) {
        String roomField = member.getRoom().getId().toString();
        Optional<Object> cached = bestEffortRead(() -> redis.opsForHash().get(key(userId), roomField));
        if (cached.isPresent()) {
            return parseCount(cached.get());
        }
        long count = countUnread(member.getRoom().getId(), member.getLastReadMessageId());
        bestEffortWrite(() -> redis.opsForHash().put(key(userId), roomField, Long.toString(count)));
        return count;
    }

    public Map<Long, Long> unreadByRoom(Long userId, List<ChatRoomMember> memberships) {
        Map<Object, Object> cached =
                bestEffortRead(() -> redis.opsForHash().entries(key(userId))).orElse(Map.of());

        Map<Long, Long> result = new HashMap<>();
        Map<String, String> recovered = new HashMap<>();
        for (ChatRoomMember member : memberships) {
            Long roomId = member.getRoom().getId();
            Object cachedValue = cached.get(roomId.toString());
            long count;
            if (cachedValue != null) {
                count = parseCount(cachedValue);
            } else {
                count = countUnread(roomId, member.getLastReadMessageId());
                recovered.put(roomId.toString(), Long.toString(count));
            }
            result.put(roomId, count);
        }
        if (!recovered.isEmpty()) {
            bestEffortWrite(() -> redis.opsForHash().putAll(key(userId), recovered));
        }
        return result;
    }

    public long total(Long userId) {
        List<ChatRoomMember> memberships =
                members.findByUserIdAndLeftAtIsNullOrderByRoomLastMessageAtDesc(userId);
        return unreadByRoom(userId, memberships).values().stream().mapToLong(Long::longValue).sum();
    }

    @Transactional
    public void read(Long userId, Long roomId, Long messageId) {
        ChatRoomMember member =
                members.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
                        .orElseThrow(() -> new ChatException(ChatErrorCode.NOT_ROOM_MEMBER));
        boolean belongsToRoom =
                messages.findById(messageId).filter(m -> m.getRoom().getId().equals(roomId)).isPresent();
        if (!belongsToRoom) {
            throw new ChatException(ChatErrorCode.INVALID_CHAT_MESSAGE);
        }
        members.updateLastReadMessageId(member.getId(), messageId);
        bestEffortWrite(() -> redis.opsForHash().delete(key(userId), roomId.toString()));
    }

    private long countUnread(Long roomId, Long lastReadMessageId) {
        return lastReadMessageId == null
                ? messages.countByRoomId(roomId)
                : messages.countByRoomIdAndIdGreaterThan(roomId, lastReadMessageId);
    }

    private long parseCount(Object cachedValue) {
        return Long.parseLong(cachedValue.toString());
    }

    private void bestEffortWrite(Runnable redisWrite) {
        try {
            redisWrite.run();
        } catch (DataAccessException ignored) {
        }
    }

    private <T> Optional<T> bestEffortRead(Supplier<T> redisRead) {
        try {
            return Optional.ofNullable(redisRead.get());
        } catch (DataAccessException ignored) {
            return Optional.empty();
        }
    }

    private String key(Long userId) {
        return "chat:unread:" + userId;
    }
}
