package com.highpass.runspot.chat.dto;

import com.highpass.runspot.chat.domain.ChatMessageType;

import jakarta.validation.constraints.*;

import java.util.List;

public record ChatSendRequest(
        @NotNull Long roomId,
        @NotNull ChatMessageType messageType,
        @Size(max = 1000) String content,
        @Size(max = 3) List<String> imageKeys,
        @NotBlank @Size(max = 100) String clientMessageId) {
    public ChatSendRequest {
        imageKeys = imageKeys == null ? List.of() : List.copyOf(imageKeys);
    }
}
