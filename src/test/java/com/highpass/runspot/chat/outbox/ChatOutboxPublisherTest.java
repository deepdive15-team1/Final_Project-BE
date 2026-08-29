package com.highpass.runspot.chat.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ChatOutboxPublisherTest {
    @Mock ChatOutboxRepository repository;
    @Mock ChatRabbitPublisher rabbit;
    @Mock ObjectMapper mapper;
    @InjectMocks ChatOutboxPublisher publisher;

    @Test
    void 브로커_ACK를_받으면_PUBLISHED로_변경한다() throws Exception {
        ChatOutbox outbox = ChatOutbox.pending(10L, "/sub/chat/room/1", "{}");
        when(repository.findReady(
                        eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        when(mapper.writeValueAsString(any(ChatBrokerEvent.class))).thenReturn("event");
        publisher.publish();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(rabbit).publishConfirmed("event");
    }

    @Test
    void 발행에_실패하면_재시도_횟수와_시간을_갱신한다() throws Exception {
        ChatOutbox outbox = ChatOutbox.pending(10L, "/sub/chat/room/1", "{}");
        when(repository.findReady(
                        eq(OutboxStatus.PENDING), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        when(mapper.writeValueAsString(any(ChatBrokerEvent.class))).thenReturn("event");
        doThrow(new RuntimeException("broker down")).when(rabbit).publishConfirmed(anyString());
        publisher.publish();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }
}
