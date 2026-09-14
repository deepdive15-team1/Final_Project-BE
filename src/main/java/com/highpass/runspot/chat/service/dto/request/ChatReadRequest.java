package com.highpass.runspot.chat.service.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatReadRequest(@NotNull Long lastReadMessageId) {}
