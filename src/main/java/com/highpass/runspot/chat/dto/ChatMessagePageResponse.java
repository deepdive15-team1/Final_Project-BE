package com.highpass.runspot.chat.dto;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> items, Long nextCursor, boolean hasNext) {}
