package com.highpass.runspot.chat.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE = "chat.events";
    public static final String QUEUE = "chat.broadcast";
    public static final String ROUTING_KEY = "chat.message";
    public static final String DLX = "chat.events.dlx";
    public static final String DLQ = "chat.broadcast.dlq";

    @Bean
    DirectExchange chatExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange chatDlx() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue chatQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    @Bean
    Queue chatDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding chatBinding() {
        return BindingBuilder.bind(chatQueue()).to(chatExchange()).with(ROUTING_KEY);
    }

    @Bean
    Binding dlqBinding() {
        return BindingBuilder.bind(chatDlq()).to(chatDlx()).with(ROUTING_KEY);
    }
}
