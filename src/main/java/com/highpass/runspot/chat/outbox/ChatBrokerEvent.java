package com.highpass.runspot.chat.outbox;public record ChatBrokerEvent(Long eventId,String destination,String payload){}
