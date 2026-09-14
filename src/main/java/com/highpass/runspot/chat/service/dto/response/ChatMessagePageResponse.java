package com.highpass.runspot.chat.service.dto.response;

import java.util.List;

public record ChatMessagePageResponse(
        List<ChatMessageResponse> items, Long nextCursor, boolean hasNext) {}
