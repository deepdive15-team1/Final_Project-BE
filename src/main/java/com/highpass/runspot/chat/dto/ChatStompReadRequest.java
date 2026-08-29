package com.highpass.runspot.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatStompReadRequest(@NotNull Long roomId, @NotNull Long lastReadMessageId) {}
