package com.highpass.runspot.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.highpass.runspot.chat.domain.dao.ChatRoomMemberRepository;
import com.highpass.runspot.common.jwt.JwtProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

/**
 * REST 필터만으로는 STOMP SUBSCRIBE 프레임의 인가가 걸리지 않기 때문에, StompHandler가
 * SUBSCRIBE 시점에 방 멤버 여부를 직접 검증한다. 비멤버가 서로 다른 방을 구독 시도했을 때
 * 실제로 전량 차단되는지를 측정한다.
 */
@ExtendWith(MockitoExtension.class)
class StompHandlerTest {

    private static final int SUBSCRIBE_ATTEMPTS = 50;
    private static final Long OUTSIDER_USER_ID = 999L;

    @Mock private JwtProvider jwt;
    @Mock private ChatRoomMemberRepository members;

    @Test
    void 방_멤버가_아닌_사용자의_구독_시도는_전부_차단된다() {
        StompHandler handler = new StompHandler(jwt, members);
        when(members.findByRoomIdAndUserIdAndLeftAtIsNull(any(), eq(OUTSIDER_USER_ID)))
                .thenReturn(Optional.empty());

        int blocked = 0;
        for (long roomId = 1; roomId <= SUBSCRIBE_ATTEMPTS; roomId++) {
            Message<byte[]> subscribe = subscribeMessage(roomId, new StompPrincipal(OUTSIDER_USER_ID));
            try {
                handler.preSend(subscribe, null);
            } catch (MessagingException e) {
                blocked++;
            }
        }

        System.out.println(
                "[WS 구독 인가] 비멤버 계정의 서로 다른 방 " + SUBSCRIBE_ATTEMPTS
                        + "건 구독 시도 -> 차단 " + blocked + "건, 통과 " + (SUBSCRIBE_ATTEMPTS - blocked) + "건");
        assertThat(blocked).isEqualTo(SUBSCRIBE_ATTEMPTS);
    }

    @Test
    void 방_멤버는_정상적으로_구독할_수_있다() {
        StompHandler handler = new StompHandler(jwt, members);
        Long memberId = 1L;
        when(members.findByRoomIdAndUserIdAndLeftAtIsNull(any(), eq(memberId)))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(
                        com.highpass.runspot.chat.domain.ChatRoomMember.class)));

        Message<byte[]> subscribe = subscribeMessage(1L, new StompPrincipal(memberId));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> handler.preSend(subscribe, null));
    }

    private Message<byte[]> subscribeMessage(long roomId, StompPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/chat/room/" + roomId);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
