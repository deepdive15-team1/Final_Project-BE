package com.highpass.runspot.chat.outbox;

import com.fasterxml.jackson.databind.*;
import com.highpass.runspot.chat.config.RabbitMqConfig;
import com.highpass.runspot.chat.service.ChatReadService;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Component
@RequiredArgsConstructor
public class ChatBrokerConsumer {
    private final ObjectMapper mapper;
    private final ChatInboxRepository inbox;
    private final SimpMessagingTemplate messaging;
    private final ChatReadService reads;

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    @Transactional
    public void consume(String rawEvent) throws Exception {
        ChatBrokerEvent event = mapper.readValue(rawEvent, ChatBrokerEvent.class);
        if (inbox.existsByEventId(event.eventId())) {
            return;
        }
        inbox.saveAndFlush(ChatInbox.received(event.eventId()));

        JsonNode payload = mapper.readTree(event.payload());
        messaging.convertAndSend(event.destination(), payload);

        Long roomId = roomIdFrom(event.destination());
        Long senderId = senderIdFrom(payload);
        scheduleUnreadIncrementAfterCommit(roomId, senderId);
    }

    private void scheduleUnreadIncrementAfterCommit(Long roomId, Long senderId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    public void afterCommit() {
                        reads.incrementForRecipients(roomId, senderId);
                    }
                });
    }

    private Long senderIdFrom(JsonNode payload) {
        JsonNode senderId = payload.path("senderId");
        return senderId.isMissingNode() || senderId.isNull() ? null : senderId.asLong();
    }

    private Long roomIdFrom(String destination) {
        try {
            return Long.valueOf(destination.substring(destination.lastIndexOf('/') + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("채팅 destination이 올바르지 않습니다.", e);
        }
    }
}
