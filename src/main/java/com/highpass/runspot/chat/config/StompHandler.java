package com.highpass.runspot.chat.config;

import com.highpass.runspot.chat.repository.ChatRoomMemberRepository;
import com.highpass.runspot.common.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {
    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/room/";
    private static final String USER_DESTINATION_PREFIX = "/sub/chat/user/";

    private final JwtProvider jwt;
    private final ChatRoomMemberRepository members;

    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || !jwt.validateToken(token(header))) {
            throw new MessagingException("유효한 JWT가 필요합니다.");
        }
        accessor.setUser(new StompPrincipal(jwt.getUserIdFromToken(token(header))));
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Long userId = userId(accessor.getUser());
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        if (destination.startsWith(ROOM_DESTINATION_PREFIX)) {
            Long roomId = parseId(destination, ROOM_DESTINATION_PREFIX);
            boolean isMember =
                    members.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId).isPresent();
            if (!isMember) {
                throw new MessagingException("채팅방 구독 권한이 없습니다.");
            }
        }
        if (destination.startsWith(USER_DESTINATION_PREFIX)
                && !parseId(destination, USER_DESTINATION_PREFIX).equals(userId)) {
            throw new MessagingException("개인 채널 구독 권한이 없습니다.");
        }
    }

    private String token(String authorizationHeader) {
        return authorizationHeader.substring("Bearer ".length());
    }

    private Long userId(Principal principal) {
        if (!(principal instanceof StompPrincipal stompPrincipal)) {
            throw new MessagingException("인증 정보가 없습니다.");
        }
        return stompPrincipal.userId();
    }

    private Long parseId(String destination, String prefix) {
        try {
            return Long.valueOf(destination.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new MessagingException("destination 형식이 올바르지 않습니다.");
        }
    }
}
