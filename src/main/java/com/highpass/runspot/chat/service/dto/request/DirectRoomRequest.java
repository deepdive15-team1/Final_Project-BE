package com.highpass.runspot.chat.service.dto.request;

import jakarta.validation.constraints.NotNull;

public record DirectRoomRequest(@NotNull Long sessionId) {}
