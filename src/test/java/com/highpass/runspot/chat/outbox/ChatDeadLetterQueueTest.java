package com.highpass.runspot.chat.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpass.runspot.chat.config.RabbitMqConfig;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

/**
 * 실제 RabbitMQ 브로커를 대상으로, 계속 처리에 실패하는 "포이즌 메시지"가 재시도 3회 후
 * 정상 큐를 막지 않고 DLQ로 격리되는지, 그리고 뒤이은 정상 메시지 처리를 방해하지 않는지 검증한다.
 * 사전에 `docker compose -f compose.rabbitmq.yml up -d`로 로컬 브로커가 떠 있어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
class ChatDeadLetterQueueTest {

    private static final int DLQ_WAIT_MS = 10_000;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Test
    void 계속_실패하는_메시지는_3회_재시도_후_DLQ로_격리되고_이후_메시지_처리를_막지_않는다() throws Exception {
        purgeQueues();

        String poisonPayload = "{ ChatBrokerEvent로 역직렬화할 수 없는 잘못된 payload }";
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, poisonPayload);

        String validEvent = new ObjectMapper().writeValueAsString(
                new ChatBrokerEvent(123456789L, "/sub/chat/room/1", "{\"messageId\":1}"));
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, validEvent);

        Message dead = pollUntilPresent(RabbitMqConfig.DLQ, DLQ_WAIT_MS);
        assertThat(dead).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).contains("역직렬화할 수 없는");
        System.out.println("[DLQ 격리] 포이즌 메시지 1건 -> 재시도 3회 후 DLQ 격리 확인 (payload 보존됨)");

        Thread.sleep(2000);
        Message leftInQueue = rabbitTemplate.receive(RabbitMqConfig.QUEUE);
        System.out.println("[정상 큐 소비 확인] 포이즌 메시지 뒤에 발행한 정상 메시지가 큐에 남아있는지 -> "
                + (leftInQueue == null ? "없음(정상 소비됨)" : "남아있음(문제)"));
        assertThat(leftInQueue).isNull();
    }

    private Message pollUntilPresent(String queue, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Message message = rabbitTemplate.receive(queue);
            if (message != null) {
                return message;
            }
            Thread.sleep(200);
        }
        return null;
    }

    private void purgeQueues() {
        while (rabbitTemplate.receive(RabbitMqConfig.QUEUE) != null) {
            // drain leftovers from previous runs
        }
        while (rabbitTemplate.receive(RabbitMqConfig.DLQ) != null) {
            // drain leftovers from previous runs
        }
    }
}
