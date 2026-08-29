package com.highpass.runspot.chat.dto;

import com.highpass.runspot.chat.domain.*;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSendResponse(
        Long messageId,
        String clientMessageId,
        Long roomId,
        Long senderId,
        String senderName,
        boolean host,
        ChatMessageType messageType,
        String content,
        List<String> imageKeys,
        LocalDateTime createdAt) {
    public static ChatSendResponse from(ChatMessage message, boolean host) {
        return new ChatSendResponse(
                message.getId(),
                message.getClientMessageId(),
                message.getRoom().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                host,
                message.getMessageType(),
                message.getContent(),
                message.getImageKeys(),
                message.getCreatedAt());
    }
}
