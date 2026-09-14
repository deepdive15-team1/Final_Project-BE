package com.highpass.runspot.chat.service.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatStompReadRequest(@NotNull Long roomId, @NotNull Long lastReadMessageId) {}
