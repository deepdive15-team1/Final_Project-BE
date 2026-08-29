package com.highpass.runspot.chat.dto;

import com.highpass.runspot.chat.domain.*;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponse(
        Long messageId,
        Long senderId,
        String senderName,
        ChatMessageType messageType,
        String content,
        List<String> imageKeys,
        LocalDateTime createdAt) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSender() == null ? null : message.getSender().getId(),
                message.getSender() == null ? null : message.getSender().getName(),
                message.getMessageType(),
                message.getContent(),
                message.getImageKeys(),
                message.getCreatedAt());
    }
}
