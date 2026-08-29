package com.highpass.runspot.chat.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatOutboxPublisher {
    private static final int BATCH_SIZE = 50;

    private final ChatOutboxRepository repository;
    private final ChatRabbitPublisher rabbit;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString = "${chat.outbox.publish-delay-ms:1000}")
    @Transactional
    public void publish() {
        for (ChatOutbox outbox : findPendingOutboxEntries()) {
            try {
                rabbit.publishConfirmed(toBrokerEvent(outbox));
                outbox.published();
            } catch (Exception publishFailure) {
                log.warn("채팅 이벤트 발행 실패: outboxId={}", outbox.getId(), publishFailure);
                outbox.failed();
            }
        }
    }

    private List<ChatOutbox> findPendingOutboxEntries() {
        return repository.findReady(
                OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
    }

    private String toBrokerEvent(ChatOutbox outbox) throws JsonProcessingException {
        return mapper.writeValueAsString(
                new ChatBrokerEvent(outbox.getId(), outbox.getDestination(), outbox.getPayload()));
    }
}
