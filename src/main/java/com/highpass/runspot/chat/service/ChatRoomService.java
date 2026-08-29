package com.highpass.runspot.chat.service;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.chat.domain.*;
import com.highpass.runspot.chat.dto.*;
import com.highpass.runspot.chat.exception.*;
import com.highpass.runspot.chat.repository.*;
import com.highpass.runspot.session.domain.Session;
import com.highpass.runspot.session.domain.dao.SessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {
    private final ChatRoomRepository rooms;
    private final ChatRoomMemberRepository members;
    private final ChatMessageRepository messages;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final ChatReadService reads;
    private final ChatNoticeRepository notices;

    public List<ChatRoomResponse> rooms(Long userId, ChatRoomType type) {
        List<ChatRoomMember> memberships = findActiveMemberships(userId, type);
        Map<Long, ChatMessage> lastMessagesByRoomId = findLastMessages(memberships);
        Map<Long, Long> unreadCountsByRoomId = reads.unreadByRoom(userId, memberships);
        Set<Long> roomIds = roomIdsOf(memberships);
        Map<Long, String> activeNoticesByRoomId = findActiveNotices(roomIds);
        Map<Long, Integer> memberCountsByRoomId = countActiveMembers(roomIds);

        return memberships.stream()
                .map(membership -> {
                    ChatRoom room = membership.getRoom();
                    return response(
                            room,
                            lastMessagesByRoomId.get(room.getLastMessageId()),
                            memberCountsByRoomId.getOrDefault(room.getId(), 0),
                            activeNoticesByRoomId.get(room.getId()),
                            unreadCountsByRoomId.getOrDefault(room.getId(), 0L));
                })
                .toList();
    }

    public ChatRoomResponse detail(Long userId, Long roomId) {
        ChatRoomMember mine = member(roomId, userId);
        ChatRoom room = room(roomId);
        ChatMessage lastMessage =
                room.getLastMessageId() == null
                        ? null
                        : messages.findById(room.getLastMessageId()).orElse(null);
        String notice =
                notices.findByRoomIdAndActiveTrue(roomId).map(ChatNotice::getContent).orElse(null);
        return response(
                room,
                lastMessage,
                members.findByRoomIdAndLeftAtIsNull(roomId).size(),
                notice,
                reads.unread(userId, mine));
    }

    public ChatMessagePageResponse messages(Long userId, Long roomId, Long cursor, int size) {
        member(roomId, userId);
        int limit = Math.min(Math.max(size, 1), 100);
        List<ChatMessage> found = messages.findPage(roomId, cursor, PageRequest.of(0, limit + 1));
        boolean hasMore = found.size() > limit;
        List<ChatMessage> page = hasMore ? found.subList(0, limit) : found;
        Long nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getId();
        return new ChatMessagePageResponse(
                page.stream().map(ChatMessageResponse::from).toList(), nextCursor, hasMore);
    }

    @Transactional
    public DirectRoomResponse direct(Long userId, DirectRoomRequest request) {
        Optional<ChatRoom> existing = findDirectRoom(request.sessionId(), userId);
        if (existing.isPresent()) {
            return new DirectRoomResponse(existing.get().getId(), false);
        }
        Session session =
                sessions.findById(request.sessionId())
                        .orElseThrow(() -> error(ChatErrorCode.SESSION_NOT_FOUND));
        if (session.getHostUser().getId().equals(userId)) {
            throw error(ChatErrorCode.HOST_DIRECT_ROOM_NOT_ALLOWED);
        }
        User guest = users.findById(userId).orElseThrow(() -> error(ChatErrorCode.USER_NOT_FOUND));
        try {
            ChatRoom room = rooms.saveAndFlush(ChatRoom.direct(session, guest));
            members.saveAll(
                    List.of(
                            ChatRoomMember.join(room, session.getHostUser(), ChatMemberRole.HOST),
                            ChatRoomMember.join(room, guest, ChatMemberRole.MEMBER)));
            return new DirectRoomResponse(room.getId(), true);
        } catch (DataIntegrityViolationException concurrentCreate) {
            return findDirectRoom(request.sessionId(), userId)
                    .map(room -> new DirectRoomResponse(room.getId(), false))
                    .orElseThrow(() -> concurrentCreate);
        }
    }

    @Transactional
    public void leave(Long userId, Long roomId) {
        ChatRoomMember member = member(roomId, userId);
        if (member.getRole() == ChatMemberRole.HOST) {
            throw error(ChatErrorCode.NOT_ROOM_MEMBER);
        }
        member.leave();
    }

    @Transactional
    public void delete(Long userId, Long roomId) {
        ChatRoomMember member = member(roomId, userId);
        if (member.getRole() != ChatMemberRole.HOST) {
            throw error(ChatErrorCode.NOT_ROOM_HOST);
        }
        member.getRoom().delete();
    }

    private List<ChatRoomMember> findActiveMemberships(Long userId, ChatRoomType type) {
        return members.findByUserIdAndLeftAtIsNullOrderByRoomLastMessageAtDesc(userId).stream()
                .filter(membership -> membership.getRoom().getStatus() != ChatRoomStatus.DELETED)
                .filter(membership -> type == null || membership.getRoom().getRoomType() == type)
                .toList();
    }

    private Map<Long, ChatMessage> findLastMessages(List<ChatRoomMember> memberships) {
        Set<Long> lastMessageIds =
                memberships.stream()
                        .map(membership -> membership.getRoom().getLastMessageId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return messages.findAllById(lastMessageIds).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
    }

    private Set<Long> roomIdsOf(List<ChatRoomMember> memberships) {
        return memberships.stream()
                .map(membership -> membership.getRoom().getId())
                .collect(Collectors.toSet());
    }

    private Map<Long, String> findActiveNotices(Set<Long> roomIds) {
        return notices.findByRoomIdInAndActiveTrue(roomIds).stream()
                .collect(Collectors.toMap(notice -> notice.getRoom().getId(), ChatNotice::getContent));
    }

    private Map<Long, Integer> countActiveMembers(Set<Long> roomIds) {
        return members.countActiveByRoomIds(roomIds).stream()
                .collect(
                        Collectors.toMap(
                                RoomMemberCount::getRoomId,
                                count -> Math.toIntExact(count.getMemberCount())));
    }

    private Optional<ChatRoom> findDirectRoom(Long sessionId, Long guestId) {
        return rooms.findBySessionIdAndGuestIdAndRoomType(sessionId, guestId, ChatRoomType.DIRECT);
    }

    private ChatRoomResponse response(
            ChatRoom room, ChatMessage lastMessage, int memberCount, String notice, long unread) {
        return new ChatRoomResponse(
                room.getId(),
                room.getRoomType(),
                room.getTitle(),
                room.getSession().getId(),
                memberCount,
                notice,
                lastMessage == null ? null : lastMessage.getContent(),
                room.getLastMessageAt(),
                unread);
    }

    private ChatRoom room(Long roomId) {
        ChatRoom chatRoom =
                rooms.findDetail(roomId).orElseThrow(() -> error(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
        if (chatRoom.getStatus() == ChatRoomStatus.DELETED) {
            throw error(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        return chatRoom;
    }

    private ChatRoomMember member(Long roomId, Long userId) {
        return members.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> error(ChatErrorCode.NOT_ROOM_MEMBER));
    }

    private ChatException error(ChatErrorCode code) {
        return new ChatException(code);
    }
}
