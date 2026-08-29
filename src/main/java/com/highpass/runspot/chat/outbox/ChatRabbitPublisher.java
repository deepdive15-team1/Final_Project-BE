package com.highpass.runspot.chat.outbox;

import com.highpass.runspot.chat.config.RabbitMqConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class ChatRabbitPublisher {
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbit;

    public void publishConfirmed(String event)
            throws InterruptedException, ExecutionException, TimeoutException {
        CorrelationData correlation = new CorrelationData();
        rabbit.convertAndSend(
                RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, event, correlation);
        CorrelationData.Confirm confirm =
                correlation.getFuture().get(CONFIRM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!confirm.ack()) {
            throw new IllegalStateException("RabbitMQ NACK: " + confirm.reason());
        }
    }
}
